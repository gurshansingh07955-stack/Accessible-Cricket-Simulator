// Usage: node tools/auction_twin.js   (no network needed)
// A twin of IplAuction's bidding on a synthetic pool shaped like the real one (marquee stars, then capped and uncapped
// players by role, many cheap depth players). It compares the OLD team logic with the NEW one: how much of the purse is
// left, whether squads are balanced (keepers, spinners, fast bowlers), how even the teams are, and what players cost.
// Synthetic-but-realistic IPL pool + a port of IplAuction's bidding, to compare the OLD team logic with a NEW one.
let seed=12345; const rnd=()=>{seed=(seed*1664525+1013904223)>>>0;return seed/4294967296;};
const TEAMS=["csk","dc","gt","kkr","lsg","mi","pbks","rr","rcb","srh"];
const GROUPS=["BA","AL","WK","FA","SP"];
function makePool(){
  const pool=[];let id=0;
  const add=(g,overall,overseas,capped,n)=>{ for(let i=0;i<n;i++){ const o=Math.max(50,Math.min(96,Math.round(overall+(rnd()-.5)*10)));
     pool.push({id:"p"+(id++),g,o,overseas:overseas(i),capped:capped(i),base:o>=88?200:o>=82?150:o>=76?100:o>=70?75:o>=64?50:30}); } };
  // marquee: 28 stars
  const mq=[["BA",92,6],["AL",90,6],["WK",88,4],["FA",91,6],["SP",88,3],["BA",90,3]];
  for(const [g,o,n] of mq) add(g,o,i=>rnd()<.4,()=>true,n);
  add("BA",76,i=>rnd()<.3,i=>rnd()<.8,110); add("AL",72,i=>rnd()<.35,i=>rnd()<.7,75); add("WK",70,i=>rnd()<.3,i=>rnd()<.7,40);
  add("FA",74,i=>rnd()<.3,i=>rnd()<.75,120); add("SP",70,i=>rnd()<.3,i=>rnd()<.7,60);
  // depth: many uncapped / lesser-known players at the lowest base price, as in the real pool
  add("BA",60,i=>rnd()<.1,()=>false,60); add("AL",60,i=>rnd()<.1,()=>false,40); add("WK",58,i=>rnd()<.1,()=>false,20); add("FA",60,i=>rnd()<.1,()=>false,60); add("SP",58,i=>rnd()<.1,()=>false,30);
  return pool;
}
const nextBid=c=>c<100?c+5:c<200?c+10:c<500?c+20:c+25;
function marketValue(p){const x=Math.max(0,Math.min(.97,(p.o-52)/38.5));let v=-650*Math.log(1-x);v*=p.overseas?.7:1.2;if(!p.capped)v*=.85;return Math.max(p.base,Math.min(2600,Math.round(v)));}
const DESIRED={BA:5,AL:4,WK:2,FA:5,SP:3};
function hash(s){let h=0;for(const c of s)h=(h*31+c.charCodeAt(0))|0;return h;}
function seededRnd(a){let s=(a>>>0)||1;return()=>{s=(s*1664525+1013904223)>>>0;return s/4294967296;};}
function buildSets(pool){
  const sets=[];const mqs=pool.filter(p=>p.o>=86).sort((a,b)=>b.o-a.o); for(let i=0;i<mqs.length;i+=6)sets.push(mqs.slice(i,i+6));
  const rest=pool.filter(p=>p.o<86);
  for(const g of GROUPS){const cap=rest.filter(p=>p.g===g&&p.capped).sort((a,b)=>b.o-a.o);const unc=rest.filter(p=>p.g===g&&!p.capped).sort((a,b)=>b.o-a.o);sets.push(cap);sets.push(unc);}
  return sets;
}
// ---------- OLD logic
function maxBidOld(st,t,p){
  const sq=st.squads[t]; if(sq.length>=25)return 0; if(p.overseas&&sq.filter(x=>x.overseas).length>=8)return 0;
  const purse=st.purse[t]; const reserve=30*Math.max(0,22-sq.length-1); const aff=purse-reserve; if(aff<p.base)return 0;
  const have=sq.filter(x=>x.g===p.g).length, want=DESIRED[p.g];
  const need=have<want?1.1:have>=want+2?.65:1;
  const rr=seededRnd(st.seed^hash(t)^(hash(p.id)<<7)); const app=.85+.35*rr();
  return Math.min(Math.floor(marketValue(p)*need*app),aff,2800);
}
// ---------- NEW logic
const CORE={BA:6,AL:4,WK:2,FA:5,SP:3};
const TEAM_TASTE={}; for(const t of TEAMS){const r=seededRnd(hash(t)*7919);TEAM_TASTE[t]={BA:.9+.25*r(),AL:.9+.25*r(),WK:.9+.25*r(),FA:.9+.25*r(),SP:.9+.25*r()};}
let planCache=null,planState=null;
function remainingByGroup(st){ // players still to come (current lot onwards), best first, per group
  const m={BA:[],AL:[],WK:[],FA:[],SP:[]};
  for(let si=st.setIndex;si<st.sets.length;si++){const set=st.sets[si];for(let li=(si===st.setIndex?st.lotIndex:0);li<set.length;li++){m[set[li].g].push(set[li]);}}
  for(const g of GROUPS)m[g].sort((a,b)=>b.o-a.o);
  return m;
}
function maxBidNew(st,t,p,K){
  if(planState!==st){planState=st;planCache={rem:remainingByGroup(st),memo:new Map()};}
  const key=t+"|"+p.id; if(planCache.memo.has(key))return planCache.memo.get(key);
  const v=compute(st,t,p,K); planCache.memo.set(key,v); return v;
}
const GMAX={BA:9,AL:6,WK:4,FA:9,SP:5};   // most of one kind a squad will hold
function compute(st,t,p,K){
  const sq=st.squads[t]; if(sq.length>=25)return 0;
  const ov=sq.filter(x=>x.overseas).length; if(p.overseas&&ov>=8)return 0;
  const purse=st.purse[t]; const slotsToMin=Math.max(0,22-sq.length); const reserveMin=30*Math.max(0,slotsToMin-1);
  if(purse-reserveMin<p.base)return 0;
  const g=p.g;
  const defs={};let totalDeficit=0;
  for(const gg of GROUPS){const have=sq.filter(x=>x.g===gg&&x.o>=K.coreBar).length;defs[gg]=Math.max(0,CORE[gg]-have);totalDeficit+=defs[gg];}
  const deficit=defs[g]; const otherDeficit=totalDeficit-deficit;
  const have=sq.filter(x=>x.g===g).length;
  // the squad must keep a place for every core player still missing in the other groups
  if(sq.length+1+otherDeficit>25)return 0;
  if(deficit===0&&have>=GMAX[g])return 0;
  const rem=planCache.rem[g]; const rank=rem.findIndex(x=>x.id===p.id); const r=rank<0?99:rank;
  let factor,kind; const targetCount=deficit+K.extraTargets;
  if(deficit>0&&r<targetCount){factor=K.target;kind="T";}
  else if(deficit>0&&r<targetCount*2+3){factor=K.interested;kind="I";}
  else if(deficit>0){factor=K.opportunist;kind="O";}
  else {factor=p.o>=minWorstCore(sq,g,K)+K.upgradeMargin?K.upgrade:K.depth;kind=factor===K.upgrade?"U":"D";}
  let value=marketValue(p)*factor*TEAM_TASTE[t][g];
  if(g==="WK"&&sq.filter(x=>x.g==="WK").length===0)value*=1.2;
  if(p.overseas){ if(ov>=6&&p.o<K.overseasElite)value*=.55; else if(ov>=4&&p.o<K.overseasElite)value*=.8; }
  if(kind==="D")value=Math.min(value,Math.max(p.base*K.depthCap,p.base+25));
  // budget management
  const coreStillNeeded=Math.max(0,totalDeficit-(deficit>0?1:0));
  const coreReserve=Math.min((purse-reserveMin)*K.coreReserveMax,coreStillNeeded*K.coreSlotCost);
  const spendable=purse-reserveMin-coreReserve;
  const share=p.o>=K.starBar?K.starShare:p.o>=K.midBar?K.midShare:K.depthShare;
  const ceiling=Math.max(p.base,Math.min(spendable,purse*share));
  // money per core place still to fill, against what a core player usually costs: a rich team stretches, a poor one holds back
  const perCore=(purse-reserveMin)/Math.max(1,totalDeficit);
  const flush=Math.max(K.flushMin,Math.min(K.flushMax,perCore/K.flushRef));
  const rr=seededRnd(st.seed^hash(t)^(hash(p.id)<<7)); const app=.92+.16*rr();
  return Math.max(0,Math.floor(Math.min(value*(kind==="D"?1:flush)*app,ceiling,purse-reserveMin,2800)));
}
function minWorstCore(sq,g,K){const c=sq.filter(x=>x.g===g).map(x=>x.o).sort((a,b)=>a-b);return c.length?c[0]:0;}
// ---------- the auction loop (all ten teams AI)
function auction(pool,maxBid){
  const st={sets:buildSets(pool),setIndex:0,lotIndex:0,purse:{},squads:{},seed:Math.floor(rnd()*1e9)};
  for(const t of TEAMS){st.purse[t]=12000;st.squads[t]=[];}
  const unsold=[]; const prices={};
  const runLot=p=>{
    let bid=0,leader=null;
    for(let g=0;g<400;g++){
      const price=bid===0?p.base:nextBid(bid);
      const keen=TEAMS.filter(t=>t!==leader).map(t=>[t,maxBid(st,t,p)]).filter(x=>x[1]>=price);
      if(!keen.length)break;
      let roll=rnd()*keen.reduce((a,x)=>a+(x[1]-price+10),0),pick=keen[0][0];
      for(const [t,m] of keen){roll-=(m-price+10);if(roll<=0){pick=t;break;}}
      bid=price;leader=pick;
    }
    if(leader){st.purse[leader]-=bid;st.squads[leader].push(p);prices[p.id]=bid;}else unsold.push(p);
  };
  while(st.setIndex<st.sets.length){const set=st.sets[st.setIndex];for(st.lotIndex=0;st.lotIndex<set.length;st.lotIndex++)runLot(set[st.lotIndex]);st.setIndex++;}
  // second chance
  const again=unsold.splice(0); st.sets=[again]; st.setIndex=0; again.forEach((p,i)=>{st.lotIndex=i;runLot(p);});
  // fill to 22 at base, exactly as IplAuction.fillSquads does (keeps money back for the places still to fill)
  for(const t of TEAMS){while(st.squads[t].length<22){const sq=st.squads[t];const ov=sq.filter(x=>x.overseas).length;const purse=st.purse[t];
    const placesAfter=22-sq.length-1;
    const cands=unsold.filter(p=>p.base<=purse&&!(p.overseas&&ov>=8));
    const keepBack=purse-30*placesAfter;
    const c=cands.filter(p=>p.base<=keepBack).sort((a,b)=>b.o-a.o)[0]||cands.sort((a,b)=>a.base-b.base)[0]; if(!c)break;
    unsold.splice(unsold.indexOf(c),1); sq.push(c); st.purse[t]-=c.base; prices[c.id]=c.base;}}
  return {st,prices};
}
function xiStrength(sq){ // best 11 by overall, with at most 4 overseas, at least 1 keeper
  const s=[...sq].sort((a,b)=>b.o-a.o); const xi=[];let ov=0;
  for(const p of s){if(xi.length>=11)break;if(p.overseas&&ov>=4)continue;xi.push(p);if(p.overseas)ov++;}
  return xi.reduce((a,p)=>a+p.o,0)/Math.max(1,xi.length);
}
function report(label,maxBid,runs=40){
  const agg={left:[],leftHigh:0,wkLow:0,spLow:0,faLow:0,size:[],xi:[],xiSpread:[],top:[],mid:[],overseas8:0,spent:[]};
  for(let r=0;r<runs;r++){
    planState=null;
    const pool=makePool();const {st,prices}=auction(pool,maxBid);
    const xis=[];
    for(const t of TEAMS){const sq=st.squads[t];const left=st.purse[t];agg.left.push(left);if(left>2500)agg.leftHigh++;
      if(sq.filter(p=>p.g==="WK").length<2)agg.wkLow++; if(sq.filter(p=>p.g==="SP").length<3)agg.spLow++; if(sq.filter(p=>p.g==="FA").length<4)agg.faLow++;
      agg.size.push(sq.length); xis.push(xiStrength(sq)); agg.spent.push(12000-left); if(sq.filter(p=>p.overseas).length>=8)agg.overseas8++;}
    agg.xi.push(xis.reduce((a,b)=>a+b,0)/10); agg.xiSpread.push(Math.max(...xis)-Math.min(...xis));
    const sold=Object.entries(prices).map(([id,v])=>[pool.find(p=>p.id===id),v]).sort((a,b)=>b[0].o-a[0].o);
    agg.top.push(sold.slice(0,10).reduce((a,x)=>a+x[1],0)/10); agg.mid.push(sold.slice(60,160).reduce((a,x)=>a+x[1],0)/100);
  }
  const avg=a=>a.reduce((x,y)=>x+y,0)/a.length;
  console.log(label.padEnd(22),"purse left/team",(avg(agg.left)/100).toFixed(1)+"cr","| teams with >25cr left",(100*agg.leftHigh/(runs*10)).toFixed(0)+"%","| squad size",avg(agg.size).toFixed(1),
   "| <2 WK",(100*agg.wkLow/(runs*10)).toFixed(0)+"% <3 SP",(100*agg.spLow/(runs*10)).toFixed(0)+"% <4 FA",(100*agg.faLow/(runs*10)).toFixed(0)+"%",
   "| XI avg",avg(agg.xi).toFixed(1),"spread",avg(agg.xiSpread).toFixed(1),"| top10 price",(avg(agg.top)/100).toFixed(1)+"cr","| ranks 60-160 price",(avg(agg.mid)/100).toFixed(2)+"cr");
}
module.exports={report,maxBidOld,maxBidNew,TEAMS,auction,makePool};
if(require.main===module){
  report("OLD (before)",maxBidOld,60);
  const K={"coreBar": 68, "extraTargets": 2, "target": 1.15, "interested": 1.08, "opportunist": 0.9, "upgrade": 0.8, "depth": 0.7, "upgradeMargin": 10, "overseasElite": 84, "depthCap": 2.4, "coreReserveMax": 0.7, "coreSlotCost": 180, "starBar": 86, "midBar": 76, "starShare": 0.22, "midShare": 0.21, "depthShare": 0.08, "flushRef": 500, "flushMin": 1.15, "flushMax": 1.6};
  report("NEW (smarter)",(st,t,p)=>maxBidNew(st,t,p,K),60);
}
