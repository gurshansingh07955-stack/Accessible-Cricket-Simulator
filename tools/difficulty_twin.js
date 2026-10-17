// Faithful JS twin of MatchEngine.simulateBall for "AI batting vs USER bowling" (balanced pitch, no stadium effects).
const R = Math.random;
const L = { BOUNCER:"BOUNCER", SHORT:"SHORT", BACK:"BACK", GOOD:"GOOD", FULL:"FULL", HV:"HV", YORKER:"YORKER", FT:"FT", LFT:"LFT", LH:"LH" };
const FULL_ISH = [L.FULL,L.YORKER,L.FT,L.LFT,L.HV], SHORT_ISH=[L.SHORT,L.BOUNCER,L.LH,L.BACK];
const ALL = Object.values(L);
const C = (e,g)=>({e,g});
const COMPAT = {
  FWD_DEF:C([L.FULL,L.GOOD,L.FT,L.LFT,L.HV],[L.SHORT,L.BOUNCER,L.BACK,L.LH,L.YORKER]),
  BACK_DEF:C([L.GOOD,L.SHORT,L.BOUNCER,L.LH,L.BACK],[L.FULL,L.FT,L.LFT,L.HV,L.YORKER]),
  STRAIGHT:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]), ON:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),
  COVER:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]), SQUARE:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),
  CUT:C([L.SHORT,L.GOOD],[L.BOUNCER,L.LH,L.BACK]), LATECUT:C([L.GOOD,L.SHORT],[L.BOUNCER,L.LH,L.BACK]),
  PULL:C([L.SHORT,L.BOUNCER,L.LH],[L.GOOD,L.BACK]), HOOK:C([L.BOUNCER,L.SHORT],[L.LH,L.GOOD,L.BACK]),
  FLICK:C([L.FULL,L.GOOD,L.FT,L.LFT,L.HV],[L.SHORT,L.LH,L.BACK]), OFFDRIVE:C([L.FULL,L.FT,L.LFT,L.HV],[L.GOOD]),
  PUNCH:C([L.SHORT,L.BACK],[L.GOOD,L.LH,L.BOUNCER]), HELI:C([L.YORKER,L.FULL,L.FT,L.LFT],[L.HV,L.GOOD]),
  SWITCH:C([L.GOOD,L.FULL,L.HV],[L.BACK,L.FT,L.LFT,L.SHORT]), UPPERCUT:C([L.BOUNCER],[L.SHORT,L.LH]),
  SCOOP:C([L.YORKER,L.FT,L.LFT,L.FULL,L.HV],[]),
  SWEEP:null, REVSWEEP:null
};
const DEFENSIVE = ["FWD_DEF","BACK_DEF","LEAVE"];
const NON_DEF = Object.keys(COMPAT).filter(s=>!["FWD_DEF","BACK_DEF"].includes(s));
const SWEEP_SPIN = C([L.GOOD,L.FULL],[L.FT,L.LFT,L.SHORT,L.BACK,L.HV]);
function compat(shot,len,style){
  if(shot==="SWEEP"||shot==="REVSWEEP"){ if(style==="PACE") return "BAD"; return SWEEP_SPIN.e.includes(len)?"EXC":SWEEP_SPIN.g.includes(len)?"GOOD":"BAD"; }
  const r=COMPAT[shot]; if(!r) return "GOOD";
  if(r.e.includes(len)) return "EXC"; if(r.g.includes(len)) return "GOOD";
  if(DEFENSIVE.includes(shot)) return "GOOD"; return "BAD";
}
function footMatch(fw,len){ if(len===L.GOOD||len===L.BACK) return "N"; const f=FULL_ISH.includes(len); return fw==="FRONT"?(f?"M":"X"):(f?"X":"M"); }
const mul=(p,k,f)=>{p[k]=Math.max(0.001,p[k]*f);};
const tierFromScore=s=>s>=85?"P":s>=70?"I":s>=50?"G":s>=30?"B":"V";
// ---------- bowling side
function applyBowling(p,d,cfg){
  if(d.angle==="AROUND"){mul(p,"wicket",1.12);mul(p,"dot",1.05);mul(p,"wide",1.2);}
  const ln=d.line;
  if(ln==="OUTOFF"){mul(p,"dot",1.15);mul(p,"wicket",1.15);mul(p,"four",.85);mul(p,"six",.85);}
  else if(ln==="OFF"){mul(p,"wicket",1.2);mul(p,"dot",1.05);mul(p,"four",.9);}
  else if(ln==="MID"){mul(p,"wicket",1.05);mul(p,"one",1.1);}
  else if(ln==="LEG"){mul(p,"four",1.15);mul(p,"six",1.05);mul(p,"wicket",.85);}
  else {mul(p,"wide",2);mul(p,"four",1.2);mul(p,"wicket",.6);}
  const t=d.len;
  const T={YORKER:{wicket:1.3,dot:1.2,four:.7,six:.6},HV:{four:1.3,wicket:1.05,dot:.85},FULL:{four:1.15,wicket:1.15},
    GOOD:{wicket:1.2,dot:1.15,four:.85,six:.8},BACK:{wicket:1.15,dot:1.2,four:.8,six:.85},SHORT:{six:1.3,wicket:1.15,dot:.9},
    BOUNCER:{six:1.4,wicket:1.2,dot:.8,four:.9},FT:{four:1.8,six:1.6,wicket:.4},LFT:{four:1.5,six:1.3,wicket:.5},LH:{six:1.6,four:1.4,wicket:.5}}[t];
  for(const k in T) mul(p,k,T[k]);
  if(d.variation){mul(p,"wicket",1.1);mul(p,"dot",1.05);mul(p,"four",.9);}
  if(d.swing){mul(p,"wicket",1.1);mul(p,"four",.95);}
  const Q={P:{wicket:1.3,dot:1.2,four:.8,six:.72,wide:.3},I:{wicket:1.15,dot:1.1,four:.9,six:.87,wide:.5},G:{},
    B:{wicket:.8,four:1.2,six:1.15,wide:1.5},V:{wicket:.6,four:1.4,six:1.3,wide:1.8}}[d.tier];
  for(const k in Q) mul(p,k,Q[k]);
}
// ---------- batting side
function applyBatting(p,b,len,style,cfg){
  const fm=footMatch(b.fw,len);
  if(fm==="M"){mul(p,"wicket",.7);mul(p,"dot",.85);} else if(fm==="X"){mul(p,"wicket",1.3);mul(p,"dot",1.15);}
  const ct=compat(b.shot,len,style), def=DEFENSIVE.includes(b.shot);
  if(ct==="EXC"){mul(p,"wicket",cfg.excW);mul(p,"dot",.72);if(!def){mul(p,"four",1.9);mul(p,"six",2.0);}}
  else if(ct==="GOOD"){mul(p,"wicket",cfg.goodW);if(!def){mul(p,"four",1.2);mul(p,"six",1.2);}}
  else {mul(p,"wicket",17);mul(p,"dot",3.5);for(const k of["one","two","three"])mul(p,k,.04);mul(p,"four",.03);mul(p,"six",.03);}
  if(b.shot==="HELI"){mul(p,"six",1.5);mul(p,"four",.9);mul(p,"wicket",1.1);}
  if(b.shot==="SWITCH"){mul(p,"six",1.4);mul(p,"wicket",1.25);mul(p,"dot",1.05);}
  if(b.shot==="PUNCH"){mul(p,"four",1.1);mul(p,"wicket",.95);}
  if(b.intent){
    if(b.intent==="UP"){mul(p,"wicket",1.3);mul(p,"four",1.4);mul(p,"six",1.6);mul(p,"dot",.9);}
    else if(b.intent==="DOWN"){mul(p,"wicket",.25);mul(p,"dot",.85);mul(p,"one",1.6);mul(p,"two",1.4);mul(p,"three",1.15);mul(p,"four",.55);mul(p,"six",.1);}
    else if(b.intent==="LEFT"){mul(p,"dot",.95);mul(p,"one",1.2);mul(p,"two",1.1);mul(p,"four",1.6);mul(p,"six",1.25);}
    else { // RIGHT step out
      if(style==="SPIN"){mul(p,"wicket",.5);mul(p,"four",1.4);mul(p,"six",1.5);mul(p,"dot",.8);}
      else if([...FULL_ISH,L.GOOD].includes(len)){mul(p,"four",1.15);mul(p,"six",1.2);}
      else {mul(p,"wicket",3);mul(p,"dot",2);for(const k of["one","two","three","four","six"])mul(p,k,.3);}
    }
  }
  const T={P:{wicket:.4,dot:.62,four:1.7,six:1.85},I:{wicket:.58,dot:.78,four:1.4,six:1.5},G:{},B:{wicket:1.6,dot:1.3,four:.7,six:.6},V:{wicket:2.2,dot:1.5,four:.5,six:.4}}[b.timing];
  for(const k in T) mul(p,k,T[k]);
  if(def){mul(p,"six",.01);mul(p,"four",.04);mul(p,"three",.08);mul(p,"two",.3);mul(p,"one",1.05);mul(p,"dot",1.2);}
}
// ---------- AI batter decision (current)
function wpick(items){let t=items.reduce((a,i)=>a+i[1],0),r=R()*t;for(const [v,w] of items){r-=w;if(r<=0)return v;}return items[items.length-1][0];}
function aiBat(bat,len,style,tier,bias,line,cfg){
  const full=FULL_ISH.includes(len),short=SHORT_ISH.includes(len);
  let seenLen=len;
  const readP = cfg.readBase!=null ? Math.min(.45,Math.max(cfg.readBase,(100-bat)/100*cfg.readScale+cfg.readBase*0.5)) : 0;
  if(readP>0 && R()<readP){ // batter misjudges the length: reads a neighbouring one
    const order=[L.FT,L.LFT,L.YORKER,L.FULL,L.HV,L.GOOD,L.BACK,L.SHORT,L.LH,L.BOUNCER];
    const i=order.indexOf(len); const j=Math.max(0,Math.min(order.length-1,i+(R()<.5?-1:1))); seenLen=order[j];
  }
  const sFull=FULL_ISH.includes(seenLen),sShort=SHORT_ISH.includes(seenLen);
  const ideal=sFull?"FRONT":sShort?"BACK":(R()<.5?"FRONT":"BACK");
  const pen=bias<0?Math.min(.12,-bias*.15):0;
  const acc=Math.max(.35,.5+(bat/100)*.4-pen);
  const fw=R()<acc?ideal:(ideal==="FRONT"?"BACK":"FRONT");
  const base={P:.15,I:.3,G:.55,B:.8,V:.95};
  const aggr=Math.min(1,Math.max(0,base[cfg.readsTier?tier:"G"]+(bat-50)/100*.15+bias*.45));
  const att=R()<aggr;
  let shot;
  if(!att){
    const wo=line==="OUTOFF"||line==="OUTLEG";
    const lc=!wo?0:(short||len===L.GOOD)?.6:.25;
    shot=R()<lc?"LEAVE":(fw==="FRONT"?"FWD_DEF":"BACK_DEF");
  } else {
    const shots=NON_DEF;
    const exc=shots.filter(s=>compat(s,seenLen,style)==="EXC"), good=shots.filter(s=>compat(s,seenLen,style)==="GOOD");
    const pool=exc.length?exc:good.length?good:["FLICK"];
    const mis=Math.max(.03,Math.min(.3,(100-bat)/100*.35));
    shot=R()<mis?shots[Math.floor(R()*shots.length)]:pool[Math.floor(R()*pool.length)];
  }
  let intent=null;
  if(!DEFENSIVE.includes(shot)){
    const rw=20*(style==="SPIN"?1.6:.7);
    intent=wpick([["DOWN",(1-aggr)*40+10],["UP",aggr*30],["LEFT",aggr*40],["RIGHT",aggr*rw]]);
  }
  const ts=Math.min(100,Math.max(0,32+bat*.48+bias*10+(R()*84-42)+(cfg.timingShift||0)));
  return {fw,shot,intent,timing:tierFromScore(ts)};
}
// ---------- user bowler model
function userBall(model){
  const len0=wpick(model.len), line=wpick(model.line);
  const tier=wpick(model.tier);
  // resolveActualLength
  let len=len0;
  const adj=(l,toward)=>{const o=[L.YORKER,L.FULL,L.HV,L.GOOD,L.BACK,L.SHORT,L.BOUNCER]; const m={YORKER:0,FULL:1,HV:2,GOOD:3,BACK:4,SHORT:5,BOUNCER:6}; let i=m[l]; i=toward?Math.max(0,i-1):Math.min(6,i+1); return o[i];};
  if(tier==="G"){ if(R()<.15) len=adj(len0,R()<.5); }
  else if(tier==="B"||tier==="V"){
    const miss=tier==="B"?.4:.7;
    if(R()>=miss) len=adj(len0,R()<.5);
    else len={YORKER:R()<.5?L.FT:L.LFT,FULL:R()<.6?L.FT:L.LFT,BOUNCER:L.LH,SHORT:R()<.6?L.LH:L.SHORT,BACK:R()<.5?L.LH:L.BACK,HV:R()<.7?L.FT:L.LFT,GOOD:R()<.5?L.FT:L.LH}[len0];
  }
  return {len,line,tier,angle:"OVER",variation:R()<.4,swing:R()<.5};
}
const MODELS={
  skilled:{tier:[["P",.12],["I",.25],["G",.33],["B",.22],["V",.08]]},
  average:{tier:[["P",.05],["I",.15],["G",.30],["B",.30],["V",.20]]},
  novice :{tier:[["P",.02],["I",.08],["G",.25],["B",.35],["V",.30]]},
};
for(const m of Object.values(MODELS)){
  m.len=[[L.GOOD,.30],[L.FULL,.20],[L.YORKER,.10],[L.BACK,.15],[L.SHORT,.10],[L.BOUNCER,.05],[L.HV,.04],[L.GOOD,.06]];
  m.line=[["OFF",.30],["OUTOFF",.25],["MID",.25],["LEG",.10],["OUTLEG",.10]];
}
// ---------- one ball
function ball(bat,bowlerR,d,bias,diff,cfg,runOuts){
  const p={dot:.35,one:.25,two:.1,three:.02,four:.1,six:.05,wicket:cfg.baseWicket,wide:.02,noBall:.018};
  const sd=bat/100-bowlerR/100;
  p.dot-=sd*.1;p.four+=sd*.05;p.six+=sd*.03;p.wicket-=sd*.05;
  for(const k in p)p[k]=Math.max(p[k],.01);
  const mod={EASY:.1,MEDIUM:0,HARD:-.1,HARDEST:-.2}[diff];
  if(cfg.diffMode==="mult"){ // proposed: multiplicative, never negative
    const wm=(cfg.wm||{EASY:1.3,MEDIUM:1,HARD:.85,HARDEST:.72})[diff], bm=(cfg.bm||{EASY:.88,MEDIUM:1,HARD:1.1,HARDEST:1.2})[diff];
    p.wicket*=wm;p.four*=bm;p.six*=bm;
  } else { p.four-=mod*.5;p.six-=mod*.3;p.wicket+=mod*.3; }
  applyBowling(p,d,cfg);
  const b=aiBat(bat,d.len,"PACE",d.tier,bias,d.line,cfg);
  if(b.shot!=="LEAVE") applyBatting(p,b,d.len,"PACE",cfg);
  if(cfg.aiWkt) p.wicket*=cfg.aiWkt;
  if(cfg.aiBnd){p.four*=cfg.aiBnd;p.six*=cfg.aiBnd;}
  p.noBall=0;
  if(cfg.wicketFloor) p.wicket=Math.max(p.wicket,cfg.wicketFloor);
  const keys=["dot","one","two","three","four","six","wicket","wide"];
  let tot=0;for(const k of keys)tot+=p[k];
  let r=R()*tot,out="dot";for(const k of keys){r-=p[k];if(r<=0){out=k;break;}}
  if(b.shot==="LEAVE"&&out!=="wide"){
    const lc={OUTOFF:.04,OFF:.40,MID:.65,LEG:.50,OUTLEG:.02}[d.line]*({YORKER:1.15,FULL:1.15,HV:1.15,FT:1.15,LFT:1.15,GOOD:.9,BACK:.55,SHORT:.2,LH:.2,BOUNCER:.05}[d.len]);
    out=R()<lc?"wicket":"dot";
  }
  return out;
}
function innings(modelName,diff,cfg,overs=20){
  const model=MODELS[modelName]; const order=[82,80,78,76,74,72,68,62,55,48,40];
  let runs=0,w=0,bi=0,balls=0,ro=0,bowlerR=cfg.bowlerR||78,legal=0,recent=[],overRuns=0;
  const T=overs*6;
  while(w<10&&legal<T){
    const bat=order[Math.min(bi,10)];
    const progress=legal/T;
    let bias;
    if(progress>=.8)bias=.95;else if(progress<.3)bias=.5;else bias=.3;
    const rw=recent.slice(-3); if(!(progress>=.8)&&progress>=.3&&rw.length>=2&&rw.every(x=>x<7))bias=.95;
    if(progress<.8){ if(progress<.35&&w>=2)bias-=Math.min(.9,.35+(w-1)*.2); else {const e=progress*7; if(w>e+1.5)bias-=Math.min(.4,(w-e)*.12);} }
    bias=Math.max(-1,Math.min(1,bias));
    const d=userBall(model);
    const o=ball(bat,bowlerR,d,bias,diff,cfg,ro);
    if(o==="wide"){runs+=1;overRuns+=1;continue;}
    legal++;
    if(o==="wicket"){w++;bi++;}
    else {
      const r={dot:0,one:1,two:2,three:3,four:4,six:6}[o]; let rr=r;
      if(r>=1&&r<=3&&ro<2&&R()<Math.min(.05,1.3/Math.max(6,overs*6*.45)*({1:.8,2:1.3,3:1.8}[r]))){w++;bi++;ro++;rr=r-1;}
      runs+=rr;overRuns+=rr;
    }
    if(legal%6===0){recent.push(overRuns);overRuns=0;}
  }
  return {runs,w};
}
function run(label,modelName,diff,cfg,n=3000){
  let R_=0,W_=0,all=0;
  for(let i=0;i<n;i++){const r=innings(modelName,diff,cfg);R_+=r.runs;W_+=r.w;if(r.w===10)all++;}
  return `${label.padEnd(38)} ${modelName.padEnd(8)} ${diff.padEnd(8)} runs ${(R_/n).toFixed(0)}  wkts ${(W_/n).toFixed(1)}  allout ${(100*all/n).toFixed(0)}%`;
}
module.exports={run,innings,MODELS};
const CUR={baseWicket:.05,excW:.22,goodW:.88,readBase:null,readScale:0,readsTier:true,diffMode:"add"};
const BAL={baseWicket:.05,excW:.22,goodW:.88,readBase:.15,readScale:.35,readsTier:true,diffMode:"mult",aiWkt:1.9,aiBnd:.6,
  wm:{EASY:1.25,MEDIUM:1,HARD:.8,HARDEST:.62}, bm:{EASY:.9,MEDIUM:1,HARD:1.12,HARDEST:1.25}};
if(require.main===module){
  // Usage: node tools/difficulty_twin.js   (no network needed)
  // Mirrors MatchEngine.simulateBall for "computer batting v USER bowling": T20, balanced pitch, bowler 78,
  // computer batters 82..40. Skill = how well the user's bowling gestures land (skilled/average/novice).
  // Field placement is not modelled, so the real game gets slightly MORE wickets than printed here.
  for(const [name,cfg] of [["BEFORE (old rules)",CUR],["AFTER (balanced rules)",BAL]]){
    console.log("=== "+name);
    for(const d of ["EASY","MEDIUM","HARD","HARDEST"]){
      const out=[];
      for(const m of ["skilled","average","novice"]) out.push(run("",m,d,cfg,2000).replace(/^.{38}\s*\S+\s+\S+\s+/,"").replace(/allout\s*(\d+)%/,"all-out $1%"));
      console.log(d.padEnd(8)+out.join(" | "));
    }
  }
}
