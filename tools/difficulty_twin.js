// Twin v2: MatchEngine.simulateBall for BOTH directions. cfg.mode "old" = the game as it is in the repo now
// (patch 023 NOT applied: AI-batter factors off), "new" = candidate symmetric rules.
const R=Math.random;
const L={BOUNCER:"BOUNCER",SHORT:"SHORT",BACK:"BACK",GOOD:"GOOD",FULL:"FULL",HV:"HV",YORKER:"YORKER",FT:"FT",LFT:"LFT",LH:"LH"};
const FULL_ISH=[L.FULL,L.YORKER,L.FT,L.LFT,L.HV], SHORT_ISH=[L.SHORT,L.BOUNCER,L.LH,L.BACK];
const C=(e,g)=>({e,g});
const COMPAT={FWD_DEF:C([L.FULL,L.GOOD,L.FT,L.LFT,L.HV],[L.SHORT,L.BOUNCER,L.BACK,L.LH,L.YORKER]),
 BACK_DEF:C([L.GOOD,L.SHORT,L.BOUNCER,L.LH,L.BACK],[L.FULL,L.FT,L.LFT,L.HV,L.YORKER]),
 STRAIGHT:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),ON:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),COVER:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),
 SQUARE:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),CUT:C([L.SHORT,L.GOOD],[L.BOUNCER,L.LH,L.BACK]),LATECUT:C([L.GOOD,L.SHORT],[L.BOUNCER,L.LH,L.BACK]),
 PULL:C([L.SHORT,L.BOUNCER,L.LH],[L.GOOD,L.BACK]),HOOK:C([L.BOUNCER,L.SHORT],[L.LH,L.GOOD,L.BACK]),
 FLICK:C([L.FULL,L.GOOD,L.FT,L.LFT,L.HV],[L.SHORT,L.LH,L.BACK]),OFFDRIVE:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),
 PUNCH:C([L.SHORT,L.BACK],[L.GOOD,L.LH,L.BOUNCER]),HELI:C([L.YORKER,L.FULL,L.FT,L.LFT],[L.HV,L.GOOD]),
 SWITCH:C([L.GOOD,L.FULL,L.HV],[L.BACK,L.FT,L.LFT,L.SHORT]),UPPERCUT:C([L.BOUNCER],[L.SHORT,L.LH]),SCOOP:C([L.YORKER,L.FT,L.LFT,L.FULL,L.HV],[]),
 SWEEP:null,REVSWEEP:null};
const DEFENSIVE=["FWD_DEF","BACK_DEF","LEAVE"];
const NON_DEF=Object.keys(COMPAT).filter(s=>!["FWD_DEF","BACK_DEF"].includes(s));
const SWEEP_SPIN=C([L.GOOD,L.FULL],[L.FT,L.LFT,L.SHORT,L.BACK,L.HV]);
function compat(shot,len,style){
 if(shot==="SWEEP"||shot==="REVSWEEP"){if(style==="PACE")return"BAD";return SWEEP_SPIN.e.includes(len)?"EXC":SWEEP_SPIN.g.includes(len)?"GOOD":"BAD";}
 const r=COMPAT[shot];if(!r)return"GOOD";if(r.e.includes(len))return"EXC";if(r.g.includes(len))return"GOOD";if(DEFENSIVE.includes(shot))return"GOOD";return"BAD";}
function footMatch(fw,len){if(len===L.GOOD||len===L.BACK)return"N";const f=FULL_ISH.includes(len);return fw==="FRONT"?(f?"M":"X"):(f?"X":"M");}
const mul=(p,k,f)=>{p[k]=Math.max(0.001,p[k]*f);};
const tierFromScore=s=>s>=85?"P":s>=70?"I":s>=50?"G":s>=30?"B":"V";
function wpick(items){let t=items.reduce((a,i)=>a+i[1],0),r=R()*t;for(const[v,w]of items){r-=w;if(r<=0)return v;}return items[items.length-1][0];}
const tri=()=>R()+R()-1; // triangular -1..1
// ---------------- bowling layer (unchanged tables)
function applyBowling(p,d){
 if(d.angle==="AROUND"){mul(p,"wicket",1.12);mul(p,"dot",1.05);mul(p,"wide",1.2);}
 const ln=d.line;
 if(ln==="OUTOFF"){mul(p,"dot",1.15);mul(p,"wicket",1.15);mul(p,"four",.85);mul(p,"six",.85);}
 else if(ln==="OFF"){mul(p,"wicket",1.2);mul(p,"dot",1.05);mul(p,"four",.9);}
 else if(ln==="MID"){mul(p,"wicket",1.05);mul(p,"one",1.1);}
 else if(ln==="LEG"){mul(p,"four",1.15);mul(p,"six",1.05);mul(p,"wicket",.85);}
 else{mul(p,"wide",2);mul(p,"four",1.2);mul(p,"wicket",.6);}
 const T={YORKER:{wicket:1.3,dot:1.2,four:.7,six:.6},HV:{four:1.3,wicket:1.05,dot:.85},FULL:{four:1.15,wicket:1.15},GOOD:{wicket:1.2,dot:1.15,four:.85,six:.8},
  BACK:{wicket:1.15,dot:1.2,four:.8,six:.85},SHORT:{six:1.3,wicket:1.15,dot:.9},BOUNCER:{six:1.4,wicket:1.2,dot:.8,four:.9},
  FT:{four:1.8,six:1.6,wicket:.4},LFT:{four:1.5,six:1.3,wicket:.5},LH:{six:1.6,four:1.4,wicket:.5}}[d.len];
 for(const k in T)mul(p,k,T[k]);
 if(d.vari==="cut"){mul(p,"wicket",1.1);mul(p,"dot",1.05);mul(p,"four",.9);}
 else if(d.vari==="seam"){mul(p,"wicket",1.1);mul(p,"dot",1.05);mul(p,"wide",1.1);}
 else if(d.vari==="rev"){mul(p,"wicket",1.25);mul(p,"dot",1.1);mul(p,"four",.85);}
 else if(d.vari==="slow"){mul(p,"wicket",1.1);mul(p,"six",.8);}
 if(d.swing){mul(p,"wicket",1.1);mul(p,"four",.95);}
 const Q={P:{wicket:1.3,dot:1.2,four:.8,six:.72,wide:.3},I:{wicket:1.15,dot:1.1,four:.9,six:.87,wide:.5},G:{},B:{wicket:.8,four:1.2,six:1.15,wide:1.5},V:{wicket:.6,four:1.4,six:1.3,wide:1.8}}[d.tier];
 for(const k in Q)mul(p,k,Q[k]);
}
// ---------------- batting layer
function applyBatting(p,b,len,style,cfg,tier){
 const pre={...p};
 const fm=footMatch(b.fw,len);
 if(fm==="M"){mul(p,"wicket",.7);mul(p,"dot",.85);}else if(fm==="X"){mul(p,"wicket",1.3);mul(p,"dot",1.15);}
 const ct=compat(b.shot,len,style),def=DEFENSIVE.includes(b.shot);
 if(ct==="EXC"){mul(p,"wicket",.22);mul(p,"dot",.72);if(!def){mul(p,"four",1.9);mul(p,"six",2.0);}}
 else if(ct==="GOOD"){mul(p,"wicket",.88);if(!def){mul(p,"four",1.2);mul(p,"six",1.2);}}
 else{mul(p,"wicket",cfg.badW||17);mul(p,"dot",3.5);for(const k of["one","two","three"])mul(p,k,.04);mul(p,"four",.03);mul(p,"six",.03);}
 if(b.shot==="HELI"){mul(p,"six",1.5);mul(p,"four",.9);mul(p,"wicket",1.1);}
 if(b.shot==="SWITCH"){mul(p,"six",1.4);mul(p,"wicket",1.25);mul(p,"dot",1.05);}
 if(b.shot==="PUNCH"){mul(p,"four",1.1);mul(p,"wicket",.95);}
 if(b.intent){
  if(b.intent==="UP"){mul(p,"wicket",1.3);mul(p,"four",1.4);mul(p,"six",1.6);mul(p,"dot",.9);}
  else if(b.intent==="DOWN"){mul(p,"wicket",.25);mul(p,"dot",.85);mul(p,"one",1.6);mul(p,"two",1.4);mul(p,"three",1.15);mul(p,"four",.55);mul(p,"six",.1);}
  else if(b.intent==="LEFT"){mul(p,"dot",.95);mul(p,"one",1.2);mul(p,"two",1.1);mul(p,"four",1.6);mul(p,"six",1.25);}
  else{if(style==="SPIN"){mul(p,"wicket",.5);mul(p,"four",1.4);mul(p,"six",1.5);mul(p,"dot",.8);}
   else if([...FULL_ISH,L.GOOD].includes(len)){mul(p,"four",1.15);mul(p,"six",1.2);}
   else{mul(p,"wicket",3);mul(p,"dot",2);for(const k of["one","two","three","four","six"])mul(p,k,.3);}}
 }
 const T={P:{wicket:.4,dot:.62,four:1.7,six:1.85},I:{wicket:.58,dot:.78,four:1.4,six:1.5},G:{},B:{wicket:1.6,dot:1.3,four:.7,six:.6},V:{wicket:2.2,dot:1.5,four:.5,six:.4}}[b.timing];
 for(const k in T)mul(p,k,T[k]);
 if(cfg.contest){ // the ball's quality fights back against what the batting side stacked up
  const sB=cfg.sB[tier], sP=cfg.sP[tier];
  for(const k of["four","six","dot"]){ if(p[k]>pre[k]) p[k]=pre[k]*Math.pow(p[k]/pre[k],sB); }
  if(p.wicket<pre.wicket) p.wicket=pre.wicket*Math.pow(p.wicket/pre.wicket,sP);
 }
 if(def){mul(p,"six",cfg.defSix);mul(p,"four",cfg.defFour);mul(p,"three",.08);mul(p,"two",.3);mul(p,"one",1.05);mul(p,"dot",1.2);}
}
// ---------------- one ball distribution
function dist(batR,bowlR,diff,side,ball,shot,cfg){
 const p={dot:.35,one:.25,two:.1,three:.02,four:.1,six:.05,wicket:.05,wide:.02,noBall:.018};
 const sd=batR/100-bowlR/100;p.dot-=sd*.1;p.four+=sd*.05;p.six+=sd*.03;p.wicket-=sd*.05;
 for(const k in p)p[k]=Math.max(p[k],.01);
 const mod={EASY:.1,MEDIUM:0,HARD:-.1,HARDEST:-.2}[diff];
 if(cfg.diffMult){const wm=cfg.wm[diff],bm=cfg.bm[diff]; // wm/bm describe the USER-BOWLING side; batting uses the inverse
   if(side==="userBowl"){p.wicket*=wm;p.four*=bm;p.six*=bm;}else{p.wicket/=wm;p.four/=bm;p.six/=bm;}}
 else if(side==="userBat"){p.four+=mod*.5;p.six+=mod*.3;p.wicket-=mod*.3;}
 else{p.four-=mod*.5;p.six-=mod*.3;p.wicket+=mod*.3;}
 applyBowling(p,ball);
 if(shot.shot!=="LEAVE")applyBatting(p,shot,ball.len,"PACE",cfg,ball.tier);
 p.noBall=0;
 return p;
}
function sample(p){const keys=["dot","one","two","three","four","six","wicket","wide"];let t=0;for(const k of keys)t+=p[k];let r=R()*t;for(const k of keys){r-=p[k];if(r<=0)return k;}return"dot";}
function normalised(p){const keys=["dot","one","two","three","four","six","wicket","wide"];let t=0;for(const k of keys)t+=p[k];const o={};for(const k of keys)o[k]=p[k]/t;return o;}
function leaveOutcome(ball){const lc={OUTOFF:.04,OFF:.40,MID:.65,LEG:.50,OUTLEG:.02}[ball.line]*({YORKER:1.15,FULL:1.15,HV:1.15,FT:1.15,LFT:1.15,GOOD:.9,BACK:.55,SHORT:.2,LH:.2,BOUNCER:.05}[ball.len]);return R()<lc?"wicket":"dot";}
// ---------------- computer bowler (current generateAiBowlingDecision or proposed)
function adj(l,toward){const o=[L.YORKER,L.FULL,L.HV,L.GOOD,L.BACK,L.SHORT,L.BOUNCER];const m={YORKER:0,FULL:1,HV:2,GOOD:3,BACK:4,SHORT:5,BOUNCER:6};let i=m[l];i=toward?Math.max(0,i-1):Math.min(6,i+1);return o[i];}
function resolveLen(len0,tier){
 let len=len0;
 if(tier==="G"){if(R()<.15)len=adj(len0,R()<.5);}
 else if(tier==="B"||tier==="V"){const miss=tier==="B"?.4:.7;
  if(R()>=miss)len=adj(len0,R()<.5);
  else len={YORKER:R()<.5?L.FT:L.LFT,FULL:R()<.6?L.FT:L.LFT,BOUNCER:L.LH,SHORT:R()<.6?L.LH:L.SHORT,BACK:R()<.5?L.LH:L.BACK,HV:R()<.7?L.FT:L.LFT,GOOD:R()<.5?L.FT:L.LH}[len0];}
 return len;
}
function aiBowl(bowlR,cfg){
 const angle=wpick([["OVER",65],["AROUND",35]]);
 const line=wpick([["OUTOFF",3],["OFF",3],["MID",2],["LEG",1],["OUTLEG",.5]]);
 const vari=wpick([["none",60],["cut",10],["seam",10],["rev",10],["slow",10]]);
 const len0=wpick([[L.BOUNCER,.5],[L.SHORT,1],[L.BACK,1.5],[L.GOOD,3],[L.FULL,3],[L.HV,1.5],[L.YORKER,.5]]);
 let score;
 if(cfg.aiBowlNew) score=Math.min(100,Math.max(0,cfg.bowlBase+bowlR*cfg.bowlSlope+tri()*cfg.bowlSpread));
 else score=Math.min(100,Math.max(0,25+bowlR*.45+(R()*96-48)));
 const tier=tierFromScore(score);
 const len=resolveLen(len0,tier);
 return{len,line,tier,angle,vari,swing:vari==="none"&&R()<.5};
}
// ---------------- computer batter (current generateAiBattingDecision)
function aiBat(bat,ball,bias,cfg){
 let seen=ball.len;
 if(cfg.readP&&R()<Math.min(.45,Math.max(cfg.readP,(100-bat)/100*.35+cfg.readP*.5))){
  const o=[L.FT,L.LFT,L.YORKER,L.FULL,L.HV,L.GOOD,L.BACK,L.SHORT,L.LH,L.BOUNCER];const i=o.indexOf(ball.len);seen=o[Math.max(0,Math.min(9,i+(R()<.5?-1:1)))];}
 const sFull=FULL_ISH.includes(seen),sShort=SHORT_ISH.includes(seen);
 const ideal=sFull?"FRONT":sShort?"BACK":(R()<.5?"FRONT":"BACK");
 const pen=bias<0?Math.min(.12,-bias*.15):0;const acc=Math.max(.35,.5+(bat/100)*.4-pen);
 const fw=R()<acc?ideal:(ideal==="FRONT"?"BACK":"FRONT");
 const base={P:.15,I:.3,G:.55,B:.8,V:.95};
 const aggr=Math.min(1,Math.max(0,base[ball.tier]+(bat-50)/100*.15+bias*.45));
 let shot;
 if(R()>=aggr){const wo=ball.line==="OUTOFF"||ball.line==="OUTLEG";const lc=!wo?0:(SHORT_ISH.includes(ball.len)||ball.len===L.GOOD)?.6:.25;shot=R()<lc?"LEAVE":(fw==="FRONT"?"FWD_DEF":"BACK_DEF");}
 else{const exc=NON_DEF.filter(s=>compat(s,seen,"PACE")==="EXC"),good=NON_DEF.filter(s=>compat(s,seen,"PACE")==="GOOD");
  const pool=exc.length?exc:good.length?good:["FLICK"];const mis=Math.max(.03,Math.min(.3,(100-bat)/100*.35));
  shot=R()<mis?NON_DEF[Math.floor(R()*NON_DEF.length)]:pool[Math.floor(R()*pool.length)];}
 let intent=null;if(!DEFENSIVE.includes(shot)){intent=wpick([["DOWN",(1-aggr)*40+10],["UP",aggr*30],["LEFT",aggr*40],["RIGHT",aggr*14]]);}
 const spread=cfg.aiBatNew?cfg.batSpread:42;
 const noise=cfg.aiBatNew?tri()*spread:(R()*84-42);
 const ts=Math.min(100,Math.max(0,32+(cfg.timeShift||0)+bat*.48+bias*10+noise));
 return{fw,shot,intent,timing:tierFromScore(ts)};
}
// ---------------- human models
const HUMAN_BAT={ // user batting: how well the human reads the ball and times it (skilled ~ the computer batter)
 skilled:{phase:true,att:.55,attTier:{P:.30,I:.42,G:.58,B:.75,V:.88},intentW:[["DOWN",22],["LEFT",33],["UP",30],["RIGHT",5],[null,10]],exc:.80,good:.17,fw:.85,tier:[["P",.17],["I",.28],["G",.28],["B",.18],["V",.09]]},
 average:{phase:true,att:.50,attTier:{P:.28,I:.38,G:.52,B:.66,V:.78},exc:.60,good:.28,fw:.70,tier:[["P",.08],["I",.20],["G",.30],["B",.26],["V",.16]]},
 novice :{att:.45,exc:.40,good:.35,fw:.55,tier:[["P",.03],["I",.10],["G",.27],["B",.34],["V",.26]]}};
function humanShot(h,ball,phase){
 const fullish=FULL_ISH.includes(ball.len),short=SHORT_ISH.includes(ball.len);
 const ideal=fullish?"FRONT":short?"BACK":(R()<.5?"FRONT":"BACK");
 const fw=R()<h.fw?ideal:(ideal==="FRONT"?"BACK":"FRONT");
 let shot;
 let attP=h.attTier?h.attTier[ball.tier]:h.att; if(h.phase) attP=Math.min(1,attP+(phase<.3?.15:phase>=.8?.30:0));
 if(R()>=attP){shot=R()<.5?"FWD_DEF":"BACK_DEF";}
 else{const roll=R();const want=roll<h.exc?"EXC":roll<h.exc+h.good?"GOOD":"BAD";
  let pool=NON_DEF.filter(s=>compat(s,ball.len,"PACE")===want);if(!pool.length)pool=NON_DEF;shot=pool[Math.floor(R()*pool.length)];}
 let intent=null;if(!DEFENSIVE.includes(shot))intent=wpick(h.intentW||[["DOWN",30],["LEFT",30],["UP",22],["RIGHT",4],[null,14]]);
 return{fw,shot,intent,timing:wpick(h.tier)};
}
const HUMAN_BOWL={skilled:[["P",.15],["I",.27],["G",.33],["B",.20],["V",.05]],average:[["P",.08],["I",.20],["G",.32],["B",.28],["V",.12]],novice:[["P",.03],["I",.10],["G",.27],["B",.34],["V",.26]]};
function humanBall(skill){
 const len0=wpick([[L.GOOD,.36],[L.FULL,.20],[L.YORKER,.10],[L.BACK,.15],[L.SHORT,.10],[L.BOUNCER,.05],[L.HV,.04]]);
 const line=wpick([["OFF",.30],["OUTOFF",.25],["MID",.25],["LEG",.10],["OUTLEG",.10]]);
 const tier=wpick(HUMAN_BOWL[skill]);const len=resolveLen(len0,tier);
 return{len,line,tier,angle:"OVER",vari:R()<.4?"cut":"none",swing:R()<.5};
}
// ---------------- innings
const ORDER=[82,80,78,76,74,72,68,62,55,48,40];
function runOutCheck(r,ro,overs){return r>=1&&r<=3&&ro<2&&R()<Math.min(.05,1.3/Math.max(6,overs*6*.45)*({1:.8,2:1.3,3:1.8}[r]));}
function inningsUserBowl(skill,diff,cfg,overs=20){
 let runs=0,w=0,bi=0,legal=0,ro=0,recent=[],orr=0;const T=overs*6;
 while(w<10&&legal<T){
  const bat=ORDER[Math.min(bi,10)];const pr=legal/T;
  let bias=pr>=.8?.95:pr<.3?.5:.3;const rw=recent.slice(-3);if(pr<.8&&pr>=.3&&rw.length>=2&&rw.every(x=>x<7))bias=.95;
  if(pr<.8){if(pr<.35&&w>=2)bias-=Math.min(.9,.35+(w-1)*.2);else{const e=pr*7;if(w>e+1.5)bias-=Math.min(.4,(w-e)*.12);}}
  bias=Math.max(-1,Math.min(1,bias));
  const ball=humanBall(skill),shot=aiBat(bat,ball,bias,cfg);
  let out;
  if(shot.shot==="LEAVE"){const p=dist(bat,78,diff,"userBowl",ball,{shot:"LEAVE"},cfg);out=sample({...p,wicket:0,dot:0,one:0,two:0,three:0,four:0,six:0,wide:p.wide});if(out!=="wide")out=leaveOutcome(ball);}
  else{const p=dist(bat,78,diff,"userBowl",ball,shot,cfg);if(cfg.aiWkt){p.wicket*=cfg.aiWkt;p.four*=cfg.aiBnd;p.six*=cfg.aiBnd;}out=sample(p);}
  if(out==="wide"){runs++;orr++;continue;}
  legal++;
  if(out==="wicket"){w++;bi++;}else{const r={dot:0,one:1,two:2,three:3,four:4,six:6}[out];let rr=r;if(runOutCheck(r,ro,overs)){w++;bi++;ro++;rr=r-1;}runs+=rr;orr+=rr;}
  if(legal%6===0){recent.push(orr);orr=0;}
 }
 return{runs,w};
}
const BOWLERS=[85,80,76,72,66];
function inningsUserBat(skill,diff,cfg,overs=20){
 const h=HUMAN_BAT[skill];let runs=0,w=0,bi=0,legal=0,ro=0;const T=overs*6;
 while(w<10&&legal<T){
  const bat=ORDER[Math.min(bi,10)];const over=Math.floor(legal/6);const bowlR=BOWLERS[over%5];
  const ball=aiBowl(bowlR,cfg),shot=humanShot(h,ball,legal/T);
  let out;
  if(shot.shot==="LEAVE")out="dot";else{const p=dist(bat,bowlR,diff,"userBat",ball,shot,cfg);out=sample(p);}
  if(out==="wide"){runs++;continue;}
  legal++;
  if(out==="wicket"){w++;bi++;}else{const r={dot:0,one:1,two:2,three:3,four:4,six:6}[out];let rr=r;if(runOutCheck(r,ro,overs)){w++;bi++;ro++;rr=r-1;}runs+=rr;}
 }
 return{runs,w};
}
function avg(fn,n){let a=0,b=0,all=0;for(let i=0;i<n;i++){const r=fn();a+=r.runs;b+=r.w;if(r.w===10)all++;}return{runs:a/n,w:b/n,all:100*all/n};}
module.exports={dist,normalised,avg,inningsUserBat,inningsUserBowl,aiBowl,aiBat,humanBall,L,tierFromScore,tri};
// computer v computer with the SAME decision makers: the neutral baseline (what "equal" should look like)
function inningsAiAi(cfg,overs=20){
 let runs=0,w=0,bi=0,legal=0,ro=0,recent=[],orr=0;const T=overs*6;
 while(w<10&&legal<T){
  const bat=ORDER[Math.min(bi,10)];const pr=legal/T;const bowlR=BOWLERS[Math.floor(legal/6)%5];
  let bias=pr>=.8?.95:pr<.3?.5:.3;const rw=recent.slice(-3);if(pr<.8&&pr>=.3&&rw.length>=2&&rw.every(x=>x<7))bias=.95;
  if(pr<.8){if(pr<.35&&w>=2)bias-=Math.min(.9,.35+(w-1)*.2);else{const e=pr*7;if(w>e+1.5)bias-=Math.min(.4,(w-e)*.12);}}
  bias=Math.max(-1,Math.min(1,bias));
  const ball=aiBowl(bowlR,cfg),shot=aiBat(bat,ball,bias,cfg);
  let out;
  if(shot.shot==="LEAVE"){const p=dist(bat,bowlR,"MEDIUM","userBat",ball,{shot:"LEAVE"},cfg);out=sample({...p,wicket:0,dot:0,one:0,two:0,three:0,four:0,six:0,wide:p.wide});if(out!=="wide")out=leaveOutcome(ball);}
  else out=sample(dist(bat,bowlR,"MEDIUM","userBat",ball,shot,cfg));
  if(out==="wide"){runs++;orr++;continue;}
  legal++;
  if(out==="wicket"){w++;bi++;}else{const r={dot:0,one:1,two:2,three:3,four:4,six:6}[out];let rr=r;if(runOutCheck(r,ro,overs)){w++;bi++;ro++;rr=r-1;}runs+=rr;orr+=rr;}
  if(legal%6===0){recent.push(orr);orr=0;}
 }
 return{runs,w};
}
module.exports.inningsAiAi=inningsAiAi;

// ------------------------------------------------------------------------------------------------
// Report. Usage: node tools/difficulty_twin.js   (no network needed)
// The twin mirrors MatchEngine.simulateBall for a T20, balanced pitch, bowlers 66..85, batters 82..40.
// "skilled / average / novice" describe how well the HUMAN reads the ball, times it, and lands his deliveries.
// Field placement is not modelled, so the real game gets slightly more wickets than printed.
if (require.main === module) {
  const OLD = {diffMult:false,contest:false,defSix:.01,defFour:.04,readP:0,aiBowlNew:false,aiBatNew:false};
  const WM={EASY:1.25,MEDIUM:1,HARD:.8,HARDEST:.62}, BM={EASY:.9,MEDIUM:1,HARD:1.12,HARDEST:1.25};
  const K=1.75, S0B={P:.5,I:.62,G:.8,B:.95,V:1.05}, S0P={P:.6,I:.72,G:.88,B:1,V:1.05};
  const sc=(S)=>{const o={};for(const t in S)o[t]=Math.max(.15,1-(1-S[t])*K);return o;};
  const NEW = {diffMult:true,wm:WM,bm:BM,contest:true,sB:sc(S0B),sP:sc(S0P),defSix:.002,defFour:.015,readP:.2,
    aiBowlNew:true,bowlBase:13,bowlSlope:.70,bowlSpread:48,aiBatNew:true,batSpread:42,timeShift:-6,badW:12};
  const shots={"perfect shot":{fw:"FRONT",shot:"CUT",intent:"LEFT",timing:"P"},"good shot":{fw:"FRONT",shot:"STRAIGHT",intent:"LEFT",timing:"G"},
    "bad shot":{fw:"FRONT",shot:"REVSWEEP",intent:"LEFT",timing:"G"},"defence":{fw:"FRONT",shot:"FWD_DEF",intent:null,timing:"P"}};
  for (const [name,cfg] of [["BEFORE (original rules)",OLD],["AFTER (balanced engine)",NEW]]) {
    console.log("\n================ "+name);
    console.log("One ball (good length, off stump, 80 v 80, Medium): % four / % six / % wicket / runs per ball");
    for (const tier of ["P","I","B"]) {
      console.log("  "+{P:"PERFECT",I:"IDEAL",B:"BAD"}[tier]+" ball");
      for (const [sn,s] of Object.entries(shots)) {
        const p=module.exports.normalised(module.exports.dist(80,80,"MEDIUM","userBat",{len:"GOOD",line:"OFF",tier,angle:"OVER",vari:"none",swing:false},s,cfg));
        console.log("    "+sn.padEnd(13)+(100*p.four).toFixed(1).padStart(5)+" /"+(100*p.six).toFixed(1).padStart(5)+" /"+(100*p.wicket).toFixed(1).padStart(5)+" /  "+(p.one+2*p.two+3*p.three+4*p.four+6*p.six).toFixed(2));
      }
    }
    console.log("Computer bowler, share of bad + very bad balls: "+[60,70,75,80,90].map(r=>{let b=0;const n=20000;for(let i=0;i<n;i++){const t=module.exports.aiBowl(r,cfg).tier;if(t==="B"||t==="V")b++;}return "rating "+r+": "+(100*b/n).toFixed(0)+"%";}).join("   "));
    for (const side of ["Bat","Bowl"]) {
      console.log(side==="Bat"?"USER BATTING, runs / wickets lost (skilled | average)":"USER BOWLING, runs / wickets taken (skilled | average)");
      for (const d of ["EASY","MEDIUM","HARD","HARDEST"]) {
        const o=[];for(const s of ["skilled","average"]){const r=module.exports.avg(()=>side==="Bat"?module.exports.inningsUserBat(s,d,cfg):module.exports.inningsUserBowl(s,d,cfg),2000);o.push(r.runs.toFixed(0)+" / "+r.w.toFixed(1));}
        console.log("  "+d.padEnd(8)+o.join("   |   "));
      }
    }
    const a=module.exports.avg(()=>module.exports.inningsAiAi(cfg),2000);
    console.log("Computer v computer at equal skill: "+a.runs.toFixed(0)+" / "+a.w.toFixed(1));
  }
}
