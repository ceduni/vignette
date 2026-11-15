export const SKINS = [
  {fill: "#FAEBD0", shade: "#E0C090", lip: "#D08060", dark: "#1A0A06"},
  {fill: "#F0C898", shade: "#D4A060", lip: "#C07050", dark: "#180806"},
  {fill: "#D9955A", shade: "#B87030", lip: "#A05030", dark: "#180806"},
  {fill: "#B86C30", shade: "#8A4818", lip: "#7A3818", dark: "#0E0502"},
  {fill: "#7A4018", shade: "#5A2808", lip: "#7A3818", dark: "#0A0300"},
  {fill: "#3C1C08", shade: "#280E02", lip: "#6A3020", dark: "#000000"},
];
export const HAIR_COLS = [
  "#100707","#241006","#3A1C0A","#5C2E12","#7B431E","#A8642D",
  "#D39A4A","#E4C76B","#C9C4BA","#B23A2E","#C96A3A","#6A4A78",
];
export const CLOTH_COLS = [
  "#8B3A28","#C87050","#D8681C","#C99A3F","#E8B84A","#6A7A50",
  "#4A5D43","#3E6E64","#7A5C88","#8878A8","#C84878","#A85848",
  "#5A3A28","#2A2622","#E8D8B8",
];
export const MOUTHS = ["smile","grin","open","ohh","sad","smirk","neutral","laugh"];
export const MOUTH_LBL = ["Smile","Grin","Open","Ohh","Sad","Smirk","Neutral","Ha!"];
export const EYES = ["open","happy","closed","wink"];
export const EYES_LBL = ["Open","Happy","Closed","Wink"];
export const ALL_HAIRS = ["short","long","curly","dreads","bun","braids","afro","wrap","pony"];
export const HAIR_LBL = {short:"Short",long:"Long",curly:"Curly",dreads:"Dreads",bun:"Bun",braids:"Braids",afro:"Afro",wrap:"Wrap",pony:"Pony"};

export const CHARS = [
  {id:"grandma",label:"Grandma",age:"elder", cloth:"#8878A8",pants:"#6A5A88",defLsh:-15,defRsh:15,hair:"bun",   hcol:8,glasses:true},
  {id:"grandpa",label:"Grandpa",age:"elder", cloth:"#7A8870",pants:"#5A6850",defLsh:-10,defRsh:10,hair:"short", hcol:8,isElder:true,glasses:true},
  {id:"dad",    label:"Dad",    age:"adult", cloth:"#6A5A45",pants:"#3A342B",defLsh:-5, defRsh:15,hair:"short", hcol:2},
  {id:"mom",    label:"Mom",    age:"adult", cloth:"#C84878",pants:"",       defLsh:-40,defRsh:40,hair:"long",  hcol:3,isSkirt:true},
  {id:"woman",  label:"Woman",  age:"adult", cloth:"#D8681C",pants:"",       defLsh:-50,defRsh:60,hair:"wrap",  hcol:9,isSkirt:true},
  {id:"man",    label:"Man",    age:"adult", cloth:"#6A9858",pants:"#3A4838",defLsh:-10,defRsh:10,hair:"dreads",hcol:1},
  {id:"teen_g", label:"Teen ♀", age:"teen",  cloth:"#5C7848",pants:"#4A3A30",defLsh:-20,defRsh:20,hair:"braids",hcol:1},
  {id:"teen_b", label:"Teen ♂", age:"teen",  cloth:"#A85848",pants:"#2A3040",defLsh:-15,defRsh:20,hair:"curly", hcol:5},
  {id:"child_g",label:"Child ♀",age:"child", cloth:"#E86858",pants:"#C99A3F",defLsh:-80,defRsh:80,hair:"pony",  hcol:3},
  {id:"child_b",label:"Child ♂",age:"child", cloth:"#3E7A70",pants:"#2A3830",defLsh:-70,defRsh:70,hair:"short", hcol:2},
  {id:"toddler",label:"Toddler",age:"toddler",cloth:"#F4A828",pants:"#D87818",defLsh:-90,defRsh:90,hair:"afro", hcol:1},
  {id:"elder2", label:"Elder",  age:"elder", cloth:"#A85830",pants:"#7A4020",defLsh:-20,defRsh:20,hair:"long",  hcol:8},
];

export const POSES = [
  {lbl:"Stand", set:(p,ch)=>poseWith(ch)},
  {lbl:"Wave",  set:(p,ch)=>poseWith(ch,{lsh:-16,lel:8,rsh:-86,rel:-58,bl:0,ht:4})},
  {lbl:"Point", set:(p,ch)=>poseWith(ch,{lsh:-18,lel:10,rsh:-68,rel:-22,bl:-2,ht:-3})},
  {lbl:"Dance", set:(p,ch)=>poseWith(ch,{lsh:-82,lel:-36,rsh:-64,rel:-42,ll:-10,lk:8,rl:16,rk:28,bl:-6,ht:8})},
];

export const BUBBLE_THEMES = {
  classic:{fill:"#FFFDF8",stroke:"#6A3A28",text:"#2B2018"},
  cream:  {fill:"#FFF1DC",stroke:"#A5672A",text:"#3A261A"},
  honey:  {fill:"#FFF0C9",stroke:"#C0892C",text:"#463208"},
  rose:   {fill:"#FFF0F2",stroke:"#C84878",text:"#4A1D2A"},
  sage:   {fill:"#EEF6E8",stroke:"#4E7A54",text:"#243528"},
};

export function ageScale(a) { return {elder:1,adult:1,teen:.88,child:.67,toddler:.46}[a]||1; }
export function headScale(a) { return {elder:1,adult:1,teen:.96,child:1.1,toddler:1.28}[a]||1; }
export function naturalPose(ch) {
  return {lsh:-Math.abs(ch.defLsh??18),lel:0,rsh:-Math.abs(ch.defRsh??18),rel:0,ll:0,lk:0,rl:0,rk:0,bl:0,ht:0};
}
export function poseWith(ch, o) { return Object.assign(naturalPose(ch), o||{}); }
export function escXML(s) {
  return String(s??'').replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;");
}

export function hairSVG(id, c) {
  const hi="rgba(255,255,255,.13)", sh="rgba(0,0,0,.14)";
  if(id==="short") return `<path d="M -20 -2 C -22 -17 -15 -29 0 -32 C 15 -29 22 -17 20 -2 C 13 -7 7 -9 0 -9 C -7 -9 -13 -7 -20 -2 Z" fill="${c}"/><path d="M -11 -25 C -3 -29 6 -28 12 -24" fill="none" stroke="${hi}" stroke-width="1.1" stroke-linecap="round" opacity=".55"/>`;
  if(id==="long")  return `<path d="M -20 -2 C -24 -18 -16 -32 0 -35 C 16 -32 24 -18 20 -2 L 22 35 C 22 49 13 56 6 56 C 7 39 6 20 5 5 C 4 -5 -4 -5 -5 5 C -6 20 -7 39 -6 56 C -13 56 -22 49 -22 35 Z" fill="${c}"/><path d="M -22 2 C -25 18 -24 39 -16 53 C -15 39 -15 20 -14 4 C -14 0 -13 -2 -12 -4 Z" fill="${c}"/><path d="M 22 2 C 25 18 24 39 16 53 C 15 39 15 20 14 4 C 14 0 13 -2 12 -4 Z" fill="${c}"/>`;
  if(id==="curly") {
    const pts=[[-18,-18,8.2],[-10,-27,9.2],[0,-31,10.2],[10,-27,9.2],[18,-18,8.2],[-21,-8,7.2],[-12,-5,7.6],[-3,-4,7.9],[7,-5,7.6],[18,-7,7.2],[-18,3,5.6],[17,3,5.6]];
    return pts.map(([x,y,r])=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${c}"/>`).join('');
  }
  if(id==="dreads") return `<path d="M -20 -2 C -23 -18 -16 -31 0 -34 C 16 -31 23 -18 20 -2 C 13 -8 7 -10 0 -10 C -7 -10 -13 -8 -20 -2 Z" fill="${c}"/><path d="M -21 -1 C -25 12 -24 29 -19 45" fill="none" stroke="${c}" stroke-width="4.8" stroke-linecap="round"/><path d="M -8 -5 C -10 10 -10 28 -8 43" fill="none" stroke="${c}" stroke-width="3.8" stroke-linecap="round"/><path d="M 8 -5 C 10 10 10 28 8 43" fill="none" stroke="${c}" stroke-width="3.8" stroke-linecap="round"/><path d="M 21 -1 C 25 12 24 29 19 45" fill="none" stroke="${c}" stroke-width="4.8" stroke-linecap="round"/>`;
  if(id==="bun")   return `<circle cx="0" cy="-33" r="9" fill="${c}"/><path d="M -20 -2 C -22 -16 -14 -27 0 -29 C 14 -27 22 -16 20 -2 C 13 -7 7 -9 0 -9 C -7 -9 -13 -7 -20 -2 Z" fill="${c}"/>`;
  if(id==="braids") {
    let s=`<path d="M -20 -2 C -22 -17 -15 -29 0 -32 C 15 -29 22 -17 20 -2 C 13 -7 7 -9 0 -9 C -7 -9 -13 -7 -20 -2 Z" fill="${c}"/>`;
    [-1,1].forEach(side=>{
      const bx=side*21;
      [0,1,2,3,4].forEach(i=>{const r=4.6-i*.45,y=2+i*8,x=bx+side*(i%2?1.6:-1.2);s+=`<circle cx="${x}" cy="${y}" r="${r}" fill="${c}"/>`;});
      s+=`<circle cx="${bx}" cy="38" r="2.1" fill="#C84838"/>`;
    });
    return s;
  }
  if(id==="afro") {
    const puffs=[[0,-26,17],[-15,-20,13],[15,-20,13],[-22,-8,11],[22,-8,11],[-23,4,9],[23,4,9],[-9,-30,12],[9,-30,12],[0,-14,19]];
    return puffs.map(([x,y,r])=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${c}"/>`).join('');
  }
  if(id==="wrap") return `<path d="M -22 -1 C -26 -20 -14 -36 0 -37 C 14 -36 26 -20 22 -1 C 14 -8 8 -10 0 -10 C -8 -10 -14 -8 -22 -1 Z" fill="${c}"/><path d="M -3 -36 C -6 -43 2 -47 7 -43 C 11 -40 8 -34 4 -35" fill="${c}"/>`;
  if(id==="pony")  return `<path d="M -20 -2 C -22 -17 -15 -29 0 -32 C 15 -29 22 -17 20 -2 C 13 -7 7 -9 0 -9 C -7 -9 -13 -7 -20 -2 Z" fill="${c}"/><path d="M 17 -18 C 30 -12 33 8 27 26 C 24 36 20 42 17 44 C 20 32 21 16 18 4" fill="${c}"/><circle cx="17.5" cy="-15" r="2.4" fill="#C84838"/>`;
  return hairSVG("short", c);
}

export function drawFace(age, sk, mouthId, eb, eyesId, glasses) {
  const dark=sk.dark||"#1A0806", eld=age==="elder", kid=age==="child"||age==="toddler";
  const eyeY=-153, esp=kid?7.3:9, esz=kid?3.45:(eld?2.75:3);
  const browY=eyeY-esz*1.85+eb, mouthY=-139+(kid?-4:0), mw=kid?6.2:7.8;
  let mouth="";
  if(mouthId==="smile")   mouth=`<path d="M ${-mw} 0 Q 0 5 ${mw} 0" fill="none" stroke="${dark}" stroke-width="2.05" stroke-linecap="round"/>`;
  if(mouthId==="grin")    mouth=`<rect x="${-mw*.72}" y="-1" width="${mw*1.44}" height="4.4" rx="2.2" fill="white" stroke="${dark}" stroke-width="1.65"/>`;
  if(mouthId==="open")    mouth=`<ellipse cx="0" cy="3" rx="${mw*.38}" ry="4" fill="${dark}"/>`;
  if(mouthId==="ohh")     mouth=`<ellipse cx="0" cy="3" rx="${mw*.36}" ry="4.6" fill="${dark}"/>`;
  if(mouthId==="sad")     mouth=`<path d="M ${-mw*.78} 4.3 Q 0 -0.8 ${mw*.78} 4.3" fill="none" stroke="${dark}" stroke-width="2.05" stroke-linecap="round"/>`;
  if(mouthId==="smirk")   mouth=`<path d="M ${-mw*.52} 2 Q ${mw*.12} -0.7 ${mw*.82} 0.8" fill="none" stroke="${dark}" stroke-width="2.05" stroke-linecap="round"/>`;
  if(mouthId==="neutral") mouth=`<path d="M ${-mw*.6} 2 H ${mw*.6}" fill="none" stroke="${dark}" stroke-width="1.95" stroke-linecap="round"/>`;
  if(mouthId==="laugh")   mouth=`<path d="M ${-mw*.84} -1 Q 0 6.6 ${mw*.84} -1 Q ${mw*.62} 7 0 7.8 Q ${-mw*.62} 7 ${-mw*.84} -1 Z" fill="${dark}"/>`;
  function eyeOpen(x){return `<ellipse cx="${x}" cy="${eyeY}" rx="${esz}" ry="${esz*1.25}" fill="white"/><circle cx="${x}" cy="${eyeY}" r="${esz*.7}" fill="${dark}"/><circle cx="${x+1}" cy="${eyeY-1}" r="${esz*.25}" fill="rgba(255,255,255,.82)"/>`;}
  function eyeHappy(x){return `<path d="M ${x-esz*1.1} ${eyeY+1} Q ${x} ${eyeY-esz*1.2} ${x+esz*1.1} ${eyeY+1}" fill="none" stroke="${dark}" stroke-width="1.9" stroke-linecap="round"/>`;}
  function eyeClosed(x){return `<path d="M ${x-esz*1.1} ${eyeY-.5} Q ${x} ${eyeY+esz*1.1} ${x+esz*1.1} ${eyeY-.5}" fill="none" stroke="${dark}" stroke-width="1.9" stroke-linecap="round"/>`;}
  let eyes="";
  if(eyesId==="happy") eyes=eyeHappy(-esp)+eyeHappy(esp);
  else if(eyesId==="closed") eyes=eyeClosed(-esp)+eyeClosed(esp);
  else if(eyesId==="wink") eyes=eyeOpen(-esp)+eyeHappy(esp);
  else eyes=eyeOpen(-esp)+eyeOpen(esp);
  const gl=glasses?`<g fill="none" stroke="${dark}" stroke-width="1.35" opacity=".85"><circle cx="${-esp}" cy="${eyeY}" r="${esz*1.85}"/><circle cx="${esp}" cy="${eyeY}" r="${esz*1.85}"/><path d="M ${-esp+esz*1.85} ${eyeY} Q 0 ${eyeY-2} ${esp-esz*1.85} ${eyeY}"/><path d="M ${-esp-esz*1.85} ${eyeY} L ${-esp-esz*2.6} ${eyeY-1.5}"/><path d="M ${esp+esz*1.85} ${eyeY} L ${esp+esz*2.6} ${eyeY-1.5}"/></g>`:"";
  return `${eyes}<path d="M ${-esp-esz*1.35} ${browY} Q ${-esp} ${browY-1.3} ${-esp+esz*1.35} ${browY}" fill="none" stroke="${dark}" stroke-width="1.55" stroke-linecap="round" opacity=".86"/><path d="M ${esp-esz*1.35} ${browY} Q ${esp} ${browY-1.3} ${esp+esz*1.35} ${browY}" fill="none" stroke="${dark}" stroke-width="1.55" stroke-linecap="round" opacity=".86"/><g transform="translate(0,${mouthY})">${mouth}</g>${gl}`;
}

export function personSVG(p, isSel) {
  const ch=CHARS.find(c=>c.id===p.charId);
  const sk=SKINS[p.skinIdx]||SKINS[1];
  const hc=HAIR_COLS[p.hairCol]||HAIR_COLS[0];
  const age=ch.age, asc=ageScale(age), hs=headScale(age);
  const sc=p.scale*asc;
  const headR={x:22*hs,y:25*hs};
  const hairId=ALL_HAIRS.includes(p.hairId)?p.hairId:"short";
  const topCol=p.topCol||ch.cloth, botCol=p.botCol||ch.pants||ch.cloth;
  function arm(sh,el,side){
    const sR=sh*Math.PI/180,eR=el*Math.PI/180,sx=side*18,sy=-103;
    const ex=sx+52*Math.sin(sR)*-side,ey=sy+52*Math.cos(sR);
    const hx=ex+46*Math.sin(sR+eR)*-side,hy=ey+46*Math.cos(sR+eR);
    return {sx,sy,ex,ey,hx,hy};
  }
  function leg(l,k,side){
    const lR=l*Math.PI/180,kR=k*Math.PI/180,hx=side*13,hy=2;
    const kx=hx+58*Math.sin(lR),ky=hy+58*Math.cos(lR);
    const fx=kx+52*Math.sin(lR+kR*side),fy=ky+52*Math.cos(lR+kR*side);
    return {hx,hy,kx,ky,fx,fy};
  }
  const L=arm(p.lsh,p.lel,-1), R=arm(p.rsh,p.rel,1);
  const LL=leg(p.ll,p.lk,-1), RL=leg(p.rl,p.rk,1);
  const shoeCol=(age==="child"||age==="toddler")?"#C84030":"#2A1818";
  const sel=isSel?`<rect x="-46" y="-232" width="92" height="368" rx="8" fill="none" stroke="#485B38" stroke-width="1.8" stroke-dasharray="5,3" opacity=".7"/>`:"";
  const footY=Math.max(LL.fy,RL.fy)+7;
  return `<g class="vm-person" data-uid="${p.uid}" transform="translate(${p.x},${p.y}) scale(${sc})" style="cursor:grab">
  ${sel}
  <ellipse cx="0" cy="${footY+4}" rx="46" ry="8.5" fill="rgba(30,15,5,.14)"/>
  <g transform="scale(${p.flip?-1:1},1)">
  <g transform="rotate(${p.bl},0,0)">
    <line x1="${LL.hx}" y1="${LL.hy}" x2="${LL.kx}" y2="${LL.ky}" stroke="${botCol}" stroke-width="15" stroke-linecap="round"/>
    <line x1="${LL.kx}" y1="${LL.ky}" x2="${LL.fx}" y2="${LL.fy}" stroke="${botCol}" stroke-width="12" stroke-linecap="round"/>
    <ellipse cx="${LL.fx}" cy="${LL.fy+7}" rx="13" ry="6" fill="${shoeCol}"/>
    <line x1="${RL.hx}" y1="${RL.hy}" x2="${RL.kx}" y2="${RL.ky}" stroke="${botCol}" stroke-width="15" stroke-linecap="round"/>
    <line x1="${RL.kx}" y1="${RL.ky}" x2="${RL.fx}" y2="${RL.fy}" stroke="${botCol}" stroke-width="12" stroke-linecap="round"/>
    <ellipse cx="${RL.fx}" cy="${RL.fy+7}" rx="13" ry="6" fill="${shoeCol}"/>
    ${ch.isSkirt?`<path fill="${botCol}" d="M -22 -10 Q -32 28 -38 94 L 38 94 Q 32 28 22 -10 Z"/>`:`<rect x="-20" y="-4" width="40" height="68" rx="4" fill="${botCol}"/>`}
    <path fill="${topCol}" d="M -23 -124 Q -22 -62 -20 0 L 20 0 Q 22 -62 23 -124 Z"/>
    ${ch.isElder?`<line x1="52" y1="-10" x2="56" y2="82" stroke="#7A5828" stroke-width="5" stroke-linecap="round"/>`:""}
    <rect x="-12" y="-138" width="24" height="17" rx="4" fill="${sk.fill}"/>
    <line x1="${L.sx}" y1="${L.sy}" x2="${L.ex}" y2="${L.ey}" stroke="${topCol}" stroke-width="17" stroke-linecap="round"/>
    <line x1="${L.ex}" y1="${L.ey}" x2="${L.hx}" y2="${L.hy}" stroke="${topCol}" stroke-width="13" stroke-linecap="round"/>
    <ellipse cx="${L.hx}" cy="${L.hy}" rx="9" ry="7" fill="${sk.fill}"/>
    <line x1="${R.sx}" y1="${R.sy}" x2="${R.ex}" y2="${R.ey}" stroke="${topCol}" stroke-width="17" stroke-linecap="round"/>
    <line x1="${R.ex}" y1="${R.ey}" x2="${R.hx}" y2="${R.hy}" stroke="${topCol}" stroke-width="13" stroke-linecap="round"/>
    <ellipse cx="${R.hx}" cy="${R.hy}" rx="9" ry="7" fill="${sk.fill}"/>
    <g transform="rotate(${p.ht},0,-150)">
      <g transform="translate(0,-150) scale(${hs.toFixed(3)})">${hairSVG(hairId,hc)}</g>
      <ellipse cx="0" cy="-150" rx="${headR.x}" ry="${headR.y}" fill="${sk.fill}"/>
      ${drawFace(age,sk,p.mouthId,p.eb,p.eyes,p.glasses)}
    </g>
  </g>
  </g>
</g>`;
}

export function personScale(p) {
  const ch=CHARS.find(c=>c.id===p.charId);
  return (p.scale||1)*ageScale(ch.age);
}
export function bubbleTheme(b) { return BUBBLE_THEMES[(b&&b.theme)||"classic"]||BUBBLE_THEMES.classic; }

export function wrapBubbleText(text, maxWidth, fontPx) {
  if(!wrapBubbleText._canvas) wrapBubbleText._canvas=document.createElement("canvas");
  const ctx=wrapBubbleText._canvas.getContext("2d");
  ctx.font=`600 ${fontPx||15}px sans-serif`;
  const lines=[];
  String(text||"").replace(/\r/g,"").split("\n").forEach((para,idx,arr)=>{
    const words=para.split(/\s+/).filter(Boolean);
    if(!words.length){lines.push("");return;}
    let line="";
    words.forEach(w=>{const t=line?line+" "+w:w;if(ctx.measureText(t).width<=maxWidth||!line){line=t;}else{lines.push(line);line=w;}});
    if(line)lines.push(line);
    if(idx<arr.length-1)lines.push("");
  });
  return lines.length?lines:[""];
}

export function bubbleMetrics(p) {
  if(!p.bubble)return null;
  const b=p.bubble, theme=bubbleTheme(b), fontPx=16;
  const style=b.style||"speech", padX=18, padTop=16, padBottom=16;
  const insetH=style==="thought"?16:(style==="shout"?22:0), insetV=style==="thought"?10:(style==="shout"?14:0);
  const maxBoxW=Math.max(120,Math.min(300,b.w||180));
  const lines=wrapBubbleText((b.text||"").trim()||"...",maxBoxW-(padX*2)-insetH,fontPx);
  if(!wrapBubbleText._mc)wrapBubbleText._mc=document.createElement("canvas");
  const mc=wrapBubbleText._mc.getContext("2d"); mc.font=`600 ${fontPx}px sans-serif`;
  const widest=Math.max(...lines.map(l=>mc.measureText(l||" ").width),42);
  const boxW=Math.max(128,Math.min(maxBoxW,widest+padX*2+insetH));
  const lineH=20, textH=Math.max(1,lines.length)*lineH;
  const boxH=Math.max(60,textH+padTop+padBottom+insetV);
  const bx=p.x+(b.dx||0), by=p.y+(b.dy||0);
  const sc=personScale(p);
  const anchorX=p.x+((p.flip?-10:10)*sc), anchorY=p.y-(145*sc);
  const side=b.side||"right";
  const localAnchorX=anchorX-bx, localAnchorY=anchorY-by;
  const tailRootX=side==="right"?Math.max(24,boxW*0.24):Math.min(boxW-24,boxW*0.76);
  const tailRootY=boxH-6;
  let tipX=localAnchorX, tipY=localAnchorY;
  const dX=tipX-tailRootX,dY=tipY-tailRootY,dist=Math.hypot(dX,dY),maxTail=118;
  if(dist>maxTail){const k=maxTail/dist;tipX=tailRootX+dX*k;tipY=tailRootY+dY*k;}
  return {b,theme,style,lines,fontPx,lineH,padTop,boxW,boxH,bx,by,localAnchorX,localAnchorY,tailRootX,tailRootY,tipX,tipY};
}

export function bubbleSVG(p, isSel) {
  const m=bubbleMetrics(p); if(!m)return "";
  const {theme,style,lines,fontPx,lineH,boxW,boxH,bx,by,tailRootX,tipX,tipY}=m;
  const textStartY=((boxH-(lines.length*lineH))/2)+(fontPx*.78);
  const textBlock=lines.map((l,i)=>`<tspan x="${(boxW/2).toFixed(1)}" y="${(textStartY+i*lineH).toFixed(1)}">${escXML(l||" ")}</tspan>`).join("");
  const rx=tailRootX, ry=boxH-4, tx=tipX, ty=tipY;
  const speechTail=`M ${rx-13} ${ry-5} Q ${(rx+tx)/2-4} ${(ry+ty)/2} ${tx.toFixed(1)} ${ty.toFixed(1)} Q ${(rx+tx)/2+8} ${(ry+ty)/2-2} ${rx+13} ${ry-7} Z`;
  const body=style==="speech"
    ?`<rect x="4" y="5" width="${boxW}" height="${boxH}" rx="20" fill="rgba(90,58,40,.15)"/><path d="${speechTail}" fill="${theme.fill}" stroke="${theme.stroke}" stroke-width="2.35" stroke-linejoin="round"/><rect x="0" y="0" width="${boxW}" height="${boxH}" rx="20" fill="${theme.fill}" stroke="${theme.stroke}" stroke-width="2.6"/>`
    :`<rect x="0" y="0" width="${boxW}" height="${boxH}" rx="20" fill="${theme.fill}" stroke="${theme.stroke}" stroke-width="2.6"/>`;
  const sel=isSel?`<rect x="-12" y="-12" width="${boxW+24}" height="${boxH+30}" rx="24" fill="none" stroke="#485B38" stroke-width="1.6" stroke-dasharray="5 4" opacity=".7"/>`:"";
  return `<g class="vm-bubble" data-uid="${p.uid}" transform="translate(${bx},${by})" style="cursor:grab">${sel}${body}<text x="0" y="0" fill="${theme.text}" font-family="sans-serif" font-size="${fontPx}" font-weight="600" text-anchor="middle">${textBlock}</text></g>`;
}
