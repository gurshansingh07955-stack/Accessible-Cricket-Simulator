// A twin of AiBallModel.sample for a whole innings, to calibrate a T10 factor. Assumptions: balanced pitch, medium
// ground (factors 1.0), bowlers rotate so each bowls max overs/5 bowlers, rating sets are typical of the game.
function rnd(){return Math.random();}
const C={WIDE:.026,NO_BALL:.007,DOT:.36,ONE:.325,TWO:.075,THREE:.006,FOUR:.10,SIX:.056,WICKET:.043};
function sample(st, p){ // p: {limit, t10:{b,w,d,r,early}}
  const {bat,bowl,faced,score,balls,target,runs}=st;
  const limit=p.limit;
  const edge=(bat-bowl)/100;
  let fB=Math.min(1.7,Math.max(.6,1+1.0*edge)), fD=Math.min(1.3,Math.max(.7,1-.5*edge)), fW=Math.min(1.9,Math.max(.45,1-1.5*edge)), fR=1;
  fW*= score>=75?1.9: score>=45?1.3:1;
  fW*= faced<6?1.35: faced<15?1.1: faced<30?.9:.75;
  fB*= faced<6?.8: faced>=20?1.12:1;
  const fr=balls/(limit*6);
  if(fr<.3){fB*=1.15;fD*=.95;} else if(fr<.75){fB*=.85;fR*=1.08;fW*=.95;} else {fB*=1.4;fW*=1.3;fD*=.85;}
  if(p.t10){ fB*=p.t10.b; fW*=p.t10.w; fD*=p.t10.d; fR*=p.t10.r; }
  if(target!=null){ const need=target-runs, left=Math.max(1,limit*6-balls), req=need*6/left;
    if(req>12){fB*=1.25;fW*=1.25;} else if(req>9.5){fB*=1.12;fW*=1.1;} else if(req<6){fB*=.9;fW*=.85;} }
  const w=[C.DOT*fD,C.ONE*fR,C.TWO*fR,C.THREE*fR,C.FOUR*fB,C.SIX*fB,C.WICKET*fW];
  const x=rnd();
  if(x<C.WIDE) return {wide:true,runs:0,wk:false};
  const nb = x<C.WIDE+C.NO_BALL; if(nb) w[6]=0;
  const tot=w.reduce((a,b)=>a+b,0); let r=rnd()*tot, i=0; for(i=0;i<7;i++){ r-=w[i]; if(r<=0) break; } if(i>6)i=6;
  const rr=[0,1,2,3,4,6,0][i];
  return {runs:rr,wk:i===6,nb};
}
function innings(batters, bowlers, p, target){
  let runs=0,wk=0,balls=0, a=0,b=1, nextIn=2; const st=batters.map(()=>({faced:0,score:0}));
  let ballsLegal=0;
  while(ballsLegal<p.limit*6 && wk<10){
    const over=Math.floor(ballsLegal/6); const bw=bowlers[over%bowlers.length];
    const s=sample({bat:batters[a],bowl:bw,faced:st[a].faced,score:st[a].score,balls:ballsLegal,target,runs},p);
    if(s.wide){runs+=1;continue;}
    ballsLegal++; st[a].faced++;
    if(s.nb) runs+=1;
    if(s.wk){ wk++; if(nextIn<batters.length){a=nextIn++;} else break; }
    else { runs+=s.runs; st[a].score+=s.runs; if(s.runs%2===1){[a,b]=[b,a];} }
    if(target!=null && runs>=target) break;
    if(ballsLegal%6===0){[a,b]=[b,a];}
  }
  return {runs,wk,balls:ballsLegal};
}
function run(label,p,N=20000){
  const bat=[82,78,76,74,72,68,64,58,48,38,30]; // an average side's order
  const bowl=[72,70,68,66,62]; // five bowlers' ratings
  let sr=0,sw=0,chaseWins=0,over100=0,sw0=0;
  for(let i=0;i<N;i++){
    const a=innings(bat,bowl,p,null); sr+=a.runs; sw+=a.wk; if(a.runs>=p.hundred) over100++;
    const c=innings(bat,bowl,p,a.runs+1); if(c.runs>=a.runs+1) chaseWins++;
  }
  console.log(label.padEnd(34),"avg",(sr/N).toFixed(1),"wkts",(sw/N).toFixed(1),"rpo",(sr/N/p.limit).toFixed(2),"chase win",(chaseWins/N*100).toFixed(0)+"%","P(>=hundred-mark)",(over100/N*100).toFixed(0)+"%");
}
run("T20 as shipped (check ~150, 6w)",{limit:20,hundred:200});
for(const [b,w,d,r] of [[1,1,1,1],[1.3,1,.9,1],[1.5,1,.85,1.0],[1.6,1,.8,1.05],[1.45,.9,.85,1.05],[1.55,.85,.8,1.05]]){
  run(`T10 b${b} w${w} d${d} r${r}`,{limit:10,hundred:130,t10:{b,w,d,r}});
}
console.log("---- wicket sweep");
for(const [b,w,d,r] of [[1.6,1.4,.8,1.05],[1.6,1.7,.8,1.05],[1.7,1.7,.78,1.05],[1.65,1.9,.8,1.05]]){
  run(`T10 b${b} w${w} d${d} r${r}`,{limit:10,hundred:130,t10:{b,w,d,r}});
}
