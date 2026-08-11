<script setup>
import {onMounted, onUnmounted, ref} from "vue";
import {useToast} from "../composables/useToast";
import {deleteScene, fetchMyScenes, saveScene, updateScene} from "../api/vignetteScenes";
import {SCENES, drawScene} from "../utils/vignetteBackgrounds";
import {
  SKINS, HAIR_COLS, CLOTH_COLS, MOUTHS, MOUTH_LBL, EYES, EYES_LBL, ALL_HAIRS, HAIR_LBL,
  CHARS, POSES, BUBBLE_THEMES, ageScale, escXML, personSVG, bubbleSVG,
} from "../utils/vignetteMakerArt";

const props = defineProps({
  scenarioId: {type: [String, Number], default: null},
});
const emit = defineEmits(["close", "insert"]);

const toast = useToast();

const savedScenes = ref([]);
const scenesPanelOpen = ref(false);
const loadingScenes = ref(false);
const savingScene = ref(false);
const saveNameInput = ref("");
const saveNameDialogOpen = ref(false);

const canUndo = ref(false);
const canRedo = ref(false);

let maker = null;
let autoSaveTimer = null;

function undoMaker() { maker?.undo(); }
function redoMaker() { maker?.redo(); }
function clearMaker() {
  if (!confirm("Clear all characters from the stage?")) return;
  maker?.clear();
}
function addBubbleFn() { maker?.addBubble(); }

async function insertIntoFrame() {
  if (!maker) return;
  maker.exportPNG(async (dataUrl) => {
    try {
      const res = await fetch(dataUrl);
      const blob = await res.blob();
      emit("insert", blob, maker.getState());
    } catch {
      toast.error("Export failed. Please try again.");
    }
  });
}

async function openScenesPanel() {
  scenesPanelOpen.value = true;
  loadingScenes.value = true;
  try {
    savedScenes.value = await fetchMyScenes();
  } catch {
    toast.error("Could not load saved scenes.");
  } finally {
    loadingScenes.value = false;
  }
}

function promptSave() {
  saveNameInput.value = "";
  saveNameDialogOpen.value = true;
}

async function confirmSave() {
  const state = maker?.getState();
  if (!state) return;
  savingScene.value = true;
  try {
    const saved = await saveScene(
      saveNameInput.value.trim() || "Untitled Scene",
      JSON.stringify(state),
    );
    savedScenes.value = [saved, ...savedScenes.value];
    localStorage.setItem("vm-autosave-scene-id", saved.id);
    toast.success("Scene saved!");
    saveNameDialogOpen.value = false;
  } catch {
    toast.error("Could not save scene.");
  } finally {
    savingScene.value = false;
  }
}

async function saveCurrentToExisting(scene) {
  const state = maker?.getState();
  if (!state) return;
  try {
    const updated = await updateScene(scene.id, scene.name, JSON.stringify(state));
    savedScenes.value = savedScenes.value.map((s) => (s.id === updated.id ? updated : s));
    toast.success("Scene updated!");
  } catch {
    toast.error("Could not update scene.");
  }
}

function loadScene(scene) {
  try {
    const state = JSON.parse(scene.sceneJson);
    maker?.setState(state);
    localStorage.setItem("vm-autosave", scene.sceneJson);
    localStorage.setItem("vm-autosave-scene-id", scene.id);
    scenesPanelOpen.value = false;
    toast.success(`Loaded "${scene.name}"`);
  } catch {
    toast.error("Could not load that scene.");
  }
}

async function removeScene(id) {
  if (!confirm("Delete this saved scene?")) return;
  try {
    await deleteScene(id);
    savedScenes.value = savedScenes.value.filter((s) => s.id !== id);
  } catch {
    toast.error("Could not delete scene.");
  }
}

function formatDate(iso) {
  return new Date(iso).toLocaleString(undefined, {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

onMounted(() => {
  maker = initMaker();

  const draft = localStorage.getItem("vm-autosave");
  if (draft) {
    try { maker.setState(JSON.parse(draft)); } catch (_) {}
  }

  autoSaveTimer = () => {
    const state = maker?.getState();
    if (state) localStorage.setItem("vm-autosave", JSON.stringify(state));
  };
  document.addEventListener("visibilitychange", autoSaveTimer);
});

onUnmounted(() => {
  document.removeEventListener("visibilitychange", autoSaveTimer);
  maker?.cleanup();
});

function initMaker() {
  let currentSceneId = "hearth";
  let people = [], selected = null, nextId = 1;
  let dragging = null, dragOff = {x:0,y:0};
  let advancedOpen = false;
  let histStack = [], future = [], histTimer = null;

  const $ = (id) => document.getElementById(id);

  function stateJSON() { return JSON.stringify({sceneId:currentSceneId,people,nextId}); }

  function updateHistBtns() {
    canUndo.value = histStack.length >= 2;
    canRedo.value = future.length > 0;
  }

  function scheduleSnapshot() {
    clearTimeout(histTimer);
    histTimer = setTimeout(() => {
      const cur = stateJSON();
      if (histStack[histStack.length-1] !== cur) {
        histStack.push(cur);
        if (histStack.length > 60) histStack.shift();
        future = [];
        localStorage.setItem("vm-autosave", cur);
      }
      updateHistBtns();
    }, 320);
  }

  function flushSnapshot() {
    clearTimeout(histTimer);
    const cur = stateJSON();
    if (histStack[histStack.length-1] !== cur) { histStack.push(cur); if(histStack.length>60)histStack.shift(); future=[]; }
  }

  function applyState(json) {
    const st = JSON.parse(json);
    currentSceneId = st.sceneId || currentSceneId;
    people = st.people || [];
    nextId = st.nextId || (Math.max(0,...people.map(p=>p.uid))+1);
    const selUid = selected && selected.uid;
    selected = people.find(p=>p.uid===selUid) || null;
    syncBgStrip();
    render(); buildPanel();
    updateHistBtns();
  }

  function undo() { flushSnapshot(); if(histStack.length<2)return; future.push(histStack.pop()); applyState(histStack[histStack.length-1]); }
  function redo() { if(!future.length)return; flushSnapshot(); const st=future.pop(); histStack.push(st); if(histStack.length>60)histStack.shift(); applyState(st); }

  function renderBg() {
    const stageEl=$("vmStage"); if(!stageEl)return;
    const W=stageEl.clientWidth||700, H=stageEl.clientHeight||500;
    const bgLayer=$("vmBgLayer"); bgLayer.innerHTML="";
    const oc=document.createElement("canvas"); oc.width=W; oc.height=H;
    drawScene(currentSceneId,oc,true);
    const img=document.createElementNS("http://www.w3.org/2000/svg","image");
    img.setAttribute("width",W); img.setAttribute("height",H);
    img.setAttribute("preserveAspectRatio","none");
    img.setAttribute("href",oc.toDataURL());
    bgLayer.appendChild(img);
  }

  function renderPeople() {
    const g=$("vmPeople"); if(!g)return;
    g.innerHTML="";
    $("vmHint").style.display=people.length?"none":"block";
    [...people].sort((a,b)=>a.z-b.z).forEach(p=>{
      const wrap=document.createElementNS("http://www.w3.org/2000/svg","g");
      wrap.innerHTML=personSVG(p,p===selected);
      const node=wrap.firstElementChild;
      node.addEventListener("mousedown",e=>{
        e.stopPropagation(); selectPerson(p);
        const r=$("vmStage").getBoundingClientRect();
        dragging={type:"person",person:p}; dragOff={x:(e.clientX-r.left)-p.x,y:(e.clientY-r.top)-p.y};
      });
      node.addEventListener("touchstart",e=>{
        e.preventDefault(); const t=e.touches[0];
        const r=$("vmStage").getBoundingClientRect();
        selectPerson(p); dragging={type:"person",person:p}; dragOff={x:(t.clientX-r.left)-p.x,y:(t.clientY-r.top)-p.y};
      },{passive:false});
      g.appendChild(node);
    });
  }

  function renderBubbles() {
    const g=$("vmBubbles"); if(!g)return;
    g.innerHTML="";
    [...people].sort((a,b)=>a.z-b.z).forEach(p=>{
      if(!p.bubble) return;
      const wrap=document.createElementNS("http://www.w3.org/2000/svg","g");
      wrap.innerHTML=bubbleSVG(p,p===selected);
      const node=wrap.firstElementChild;
      node.addEventListener("mousedown",e=>{
        e.stopPropagation(); selectPerson(p);
        const r=$("vmStage").getBoundingClientRect();
        dragging={type:"bubble",person:p}; dragOff={x:(e.clientX-r.left)-(p.x+(p.bubble.dx||0)),y:(e.clientY-r.top)-(p.y+(p.bubble.dy||0))};
      });
      node.addEventListener("touchstart",e=>{
        e.preventDefault(); const t=e.touches[0];
        const r=$("vmStage").getBoundingClientRect();
        selectPerson(p); dragging={type:"bubble",person:p}; dragOff={x:(t.clientX-r.left)-(p.x+(p.bubble.dx||0)),y:(t.clientY-r.top)-(p.y+(p.bubble.dy||0))};
      },{passive:false});
      g.appendChild(node);
    });
  }

  function render() { renderBg(); renderPeople(); renderBubbles(); scheduleSnapshot(); }
  function renderP() { renderPeople(); renderBubbles(); scheduleSnapshot(); }

  function selectPerson(p) { selected=p; renderP(); buildPanel(); }

  function deleteSel() { if(!selected)return; flushSnapshot(); people=people.filter(x=>x!==selected); selected=null; renderP(); buildPanel(); }
  function flipSel() { if(!selected)return; selected.flip=!selected.flip; renderP(); }
  function dupSel() {
    if(!selected)return; flushSnapshot();
    const c=JSON.parse(JSON.stringify(selected));
    c.uid=nextId++; c.z=Math.max(...people.map(p=>p.z))+1; c.x+=34; c.y+=10;
    people.push(c); selectPerson(c);
  }
  function sendBack() { if(selected){selected.z=Math.min(...people.map(p=>p.z))-1;renderP();} }
  function bringFront() { if(selected){selected.z=Math.max(...people.map(p=>p.z))+1;renderP();} }

  function smartBubbleFor(p) {
    const W=$("vmStage")?.clientWidth||720;
    const side=p.x>(W*0.56)?"left":"right";
    const w=170;
    return {text:"",style:"speech",theme:"classic",side,w,dx:side==="right"?46:-(w+46),dy:-250};
  }
  function addBubble() {
    if(!selected)return;
    flushSnapshot();
    if(!selected.bubble) selected.bubble=smartBubbleFor(selected);
    buildPanel(); renderP();
    setTimeout(()=>{const ta=document.getElementById("vmBubbleText");if(ta){ta.focus();ta.select();}},40);
  }

  function syncBgStrip() {
    document.querySelectorAll(".vm-bg-thumb").forEach(t=>t.classList.toggle("on",t.dataset.sceneId===currentSceneId));
  }

  function buildBgStrip() {
    const strip=$("vmBgStrip"); if(!strip)return;
    strip.innerHTML="";
    SCENES.forEach(sc=>{
      const wrap=document.createElement("div");
      wrap.className="vm-bg-thumb"+(sc.id===currentSceneId?" on":"");
      wrap.dataset.sceneId=sc.id;
      const oc=document.createElement("canvas"); oc.width=144; oc.height=92;
      drawScene(sc.id,oc,true);
      wrap.appendChild(oc);
      const lbl=document.createElement("span"); lbl.textContent=sc.label; wrap.appendChild(lbl);
      wrap.onclick=()=>{ flushSnapshot(); currentSceneId=sc.id; syncBgStrip(); render(); };
      strip.appendChild(wrap);
    });
  }

  function newPerson(ch,x,y) {
    return {uid:nextId++,charId:ch.id,x,y,scale:1,z:nextId,flip:false,skinIdx:1,
      hairId:ch.hair,hairCol:ch.hcol||0,mouthId:"smile",eyes:"open",glasses:!!ch.glasses,
      topCol:"",botCol:"",bubble:null,
      lsh:-Math.abs(ch.defLsh||20),lel:0,rsh:-Math.abs(ch.defRsh||20),rel:0,ll:0,lk:0,rl:0,rk:0,ht:0,bl:0,eb:0};
  }

  function buildLeftBar() {
    const pick=$("vmCharPick"); if(!pick)return;
    pick.innerHTML="";
    CHARS.forEach(ch=>{
      const card=document.createElement("div"); card.className="vm-char-card";
      const miniScale=.148/ageScale(ch.age);
      const mini=newPerson(ch,30,38); mini.scale=miniScale; nextId--;
      const miniSvg=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 60">${personSVG(mini,false)}</svg>`;
      const img=document.createElement("img");
      img.src="data:image/svg+xml;charset=utf-8,"+encodeURIComponent(miniSvg);
      img.style="width:44px;height:50px;display:block";
      const lbl=document.createElement("span"); lbl.textContent=ch.label;
      card.appendChild(img); card.appendChild(lbl);
      card.onclick=()=>{
        flushSnapshot();
        const sv=$("vmStage"); const W=sv.clientWidth||600, H=sv.clientHeight||500;
        const p=newPerson(ch,120+Math.random()*(W-240),H*.64+Math.random()*30-15);
        people.push(p); selectPerson(p); renderP();
      };
      pick.appendChild(card);
    });
  }

  function sl(lbl,key,min,max,val,dec,target) {
    const v=dec?parseFloat(val).toFixed(1):Math.round(val);
    return `<div class="vm-cr"><label>${lbl}</label><input type="range" min="${min}" max="${max}" step="${dec?.05:1}" value="${val}" data-key="${key}" data-dec="${dec?1:0}" data-target="${target||"person"}"><span class="vm-v" data-vk="${(target||"person")+"-"+key}">${v}</span></div>`;
  }

  function buildPanel() {
    const hdr=$("vmRph"), body=$("vmRpb"); if(!hdr||!body)return;
    if(!selected){ hdr.style.display="none"; body.innerHTML='<div class="vm-rp-empty">Click a character on stage<br>to edit them</div>'; return; }
    const p=selected, ch=CHARS.find(c=>c.id===p.charId);
    if(!ALL_HAIRS.includes(p.hairId))p.hairId="short";
    hdr.style.display="flex"; $("vmRphName").textContent=ch.label;
    body.innerHTML=`
<div class="vm-sec">Pose</div><div class="vm-opt-row" id="vmPoseR"></div>
<div class="vm-dv"></div>
<div class="vm-sec">Skin</div><div class="vm-sw-row" id="vmSkinR"></div>
<div class="vm-dv"></div>
<div class="vm-sec">Hair style</div><div class="vm-opt-row" id="vmHairR"></div>
<div class="vm-sw-row" id="vmHcolR" style="margin-top:4px"></div>
<div class="vm-dv"></div>
<div class="vm-sec">Expression</div><div class="vm-opt-row" id="vmMouthR"></div>
<div class="vm-sec" style="margin-top:4px">Eyes</div><div class="vm-opt-row" id="vmEyesR"></div>
${sl("Eyebrows","eb",-8,8,p.eb,0)}
<div class="vm-opt-row" style="margin-top:2px"><div class="vm-opt${p.glasses?" on":""}" id="vmGlassesT">Glasses</div></div>
<div class="vm-dv"></div>
<div class="vm-sec">Outfit</div>
<div class="vm-sub-lbl">Top</div><div class="vm-sw-row" id="vmTopR"></div>
<div class="vm-sub-lbl">${ch.isSkirt?"Skirt":"Bottom"}</div><div class="vm-sw-row" id="vmBotR"></div>
<div class="vm-dv"></div>
<div class="vm-sec">Bubble</div>
${p.bubble ? `
<textarea class="vm-textbox" id="vmBubbleText" placeholder="What are they saying...">${escXML(p.bubble.text||"")}</textarea>
<div class="vm-sec" style="padding-top:4px">Style</div><div class="vm-opt-row" id="vmBubStyleR"></div>
<div class="vm-sec" style="padding-top:4px">Theme</div><div class="vm-opt-row" id="vmBubThemeR"></div>
${sl("Width","w",110,260,p.bubble.w||170,0,"bubble")}
${sl("X","dx",-320,320,p.bubble.dx||0,0,"bubble")}
${sl("Y","dy",-380,-40,p.bubble.dy||-250,0,"bubble")}
<div style="margin-top:4px"><button class="vm-mini-btn vm-mini-btn--warn" data-vm-action="removeBubble">Remove Bubble</button></div>
` : `<div class="vm-bubble-tip">Add a dialogue bubble to show what this character is saying.</div>
<button class="vm-mini-btn" data-vm-action="addBubble" style="margin-top:6px">+ Add Bubble</button>`}
<div class="vm-dv"></div>
<button type="button" class="vm-adv-toggle" id="vmAdvToggle">
  <span>Advanced settings</span>
  <svg class="vm-adv-chevron${advancedOpen?" open":""}" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="11" height="11"><path d="M4 6l4 4 4-4"/></svg>
</button>
<div class="vm-adv-body" id="vmAdvBody" style="display:${advancedOpen?"block":"none"}">
<div class="vm-sec">Left arm</div>${sl("Shoulder","lsh",-120,60,p.lsh,0)}${sl("Elbow","lel",-110,110,p.lel,0)}
<div class="vm-sec">Right arm</div>${sl("Shoulder","rsh",-60,120,p.rsh,0)}${sl("Elbow","rel",-110,110,p.rel,0)}
<div class="vm-sec">Legs</div>${sl("L swing","ll",-45,45,p.ll,0)}${sl("L knee","lk",0,75,p.lk,0)}${sl("R swing","rl",-45,45,p.rl,0)}${sl("R knee","rk",0,75,p.rk,0)}
<div class="vm-sec">Body</div>${sl("Head tilt","ht",-35,35,p.ht,0)}${sl("Body lean","bl",-25,25,p.bl,0)}
</div>
<div class="vm-dv"></div>
<div class="vm-sec">Layer</div>
<div class="vm-layer-row">
  <button class="vm-layer-btn" data-vm-action="sendBack">Send back</button>
  <button class="vm-layer-btn" data-vm-action="bringFront">Bring front</button>
</div>
${sl("Size","scale",.5,2,p.scale,1)}
<div class="vm-hint-kbd">Arrows nudge · Delete removes · F flips · Ctrl+D copies</div>`;

    POSES.forEach(ps=>{ const d=document.createElement("div"); d.className="vm-opt"; d.textContent=ps.lbl; d.onclick=()=>{Object.assign(p,ps.set(p,ch));buildPanel();renderP();}; $("vmPoseR").appendChild(d); });
    SKINS.forEach((s,i)=>{ const d=document.createElement("div"); d.className="vm-sw"+(i===p.skinIdx?" on":""); d.style.cssText=`background:${s.fill};border-color:${s.shade}`; d.onclick=()=>{p.skinIdx=i;buildPanel();renderP();}; $("vmSkinR").appendChild(d); });
    ALL_HAIRS.forEach(h=>{ const d=document.createElement("div"); d.className="vm-opt"+(h===p.hairId?" on":""); d.textContent=HAIR_LBL[h]; d.onclick=()=>{p.hairId=h;buildPanel();renderP();}; $("vmHairR").appendChild(d); });
    HAIR_COLS.forEach((c,i)=>{ const d=document.createElement("div"); d.className="vm-sw vm-sw--sm"+(i===p.hairCol?" on":""); d.style.cssText=`background:${c};border-color:${i===p.hairCol?"#485B38":c}`; d.onclick=()=>{p.hairCol=i;buildPanel();renderP();}; $("vmHcolR").appendChild(d); });
    MOUTHS.forEach((m,i)=>{ const d=document.createElement("div"); d.className="vm-opt"+(m===p.mouthId?" on":""); d.textContent=MOUTH_LBL[i]; d.onclick=()=>{p.mouthId=m;buildPanel();renderP();}; $("vmMouthR").appendChild(d); });
    EYES.forEach((ey,i)=>{ const d=document.createElement("div"); d.className="vm-opt"+((p.eyes||"open")===ey?" on":""); d.textContent=EYES_LBL[i]; d.onclick=()=>{p.eyes=ey;buildPanel();renderP();}; $("vmEyesR").appendChild(d); });
    $("vmGlassesT").onclick=()=>{p.glasses=!p.glasses;buildPanel();renderP();};
    $("vmAdvToggle").onclick=()=>{advancedOpen=!advancedOpen;buildPanel();};
    const curTop=p.topCol||ch.cloth, curBot=p.botCol||ch.pants||ch.cloth;
    CLOTH_COLS.forEach(c=>{
      const d=document.createElement("div"); d.className="vm-sw vm-sw--sm"+(c===curTop?" on":""); d.style.cssText=`background:${c};border-color:${c===curTop?"#485B38":c}`; d.onclick=()=>{p.topCol=c;buildPanel();renderP();}; $("vmTopR").appendChild(d);
      const b=document.createElement("div"); b.className="vm-sw vm-sw--sm"+(c===curBot?" on":""); b.style.cssText=`background:${c};border-color:${c===curBot?"#485B38":c}`; b.onclick=()=>{p.botCol=c;buildPanel();renderP();}; $("vmBotR").appendChild(b);
    });
    if(p.bubble) {
      ["speech","thought","shout"].forEach(st=>{ const d=document.createElement("div"); d.className="vm-opt"+((p.bubble.style||"speech")===st?" on":""); d.textContent=st[0].toUpperCase()+st.slice(1); d.onclick=()=>{p.bubble.style=st;buildPanel();renderP();}; $("vmBubStyleR").appendChild(d); });
      Object.entries(BUBBLE_THEMES).forEach(([nm,th])=>{ const d=document.createElement("div"); d.className="vm-opt"+((p.bubble.theme||"classic")===nm?" on":""); d.textContent=nm[0].toUpperCase()+nm.slice(1); d.style.background=th.fill; d.style.borderColor=th.stroke; d.onclick=()=>{p.bubble.theme=nm;buildPanel();renderP();}; $("vmBubThemeR").appendChild(d); });
      const ta=$("vmBubbleText"); if(ta)ta.addEventListener("input",()=>{p.bubble.text=ta.value;renderP();});
    }
    body.querySelectorAll("[data-vm-action]").forEach(btn=>{
      const action=btn.dataset.vmAction;
      btn.addEventListener("click",()=>{
        if(action==="sendBack")sendBack();
        else if(action==="bringFront")bringFront();
        else if(action==="addBubble")addBubble();
        else if(action==="removeBubble"){if(selected){flushSnapshot();selected.bubble=null;buildPanel();renderP();}}
      });
    });
    body.querySelectorAll("input[type=range]").forEach(el=>{
      el.addEventListener("input",()=>{
        const k=el.dataset.key, dec=+el.dataset.dec, v=parseFloat(el.value), tgt=el.dataset.target||"person";
        if(tgt==="bubble"&&p.bubble)p.bubble[k]=v; else p[k]=v;
        const sp=body.querySelector(`[data-vk="${tgt}-${k}"]`); if(sp)sp.textContent=dec?v.toFixed(1):Math.round(v);
        renderP();
      });
    });
  }

  const onMouseMove=e=>{
    if(!dragging)return;
    const r=$("vmStage").getBoundingClientRect();
    if(dragging.type==="person"){dragging.person.x=e.clientX-r.left-dragOff.x;dragging.person.y=e.clientY-r.top-dragOff.y;}
    else if(dragging.type==="bubble"&&dragging.person.bubble){dragging.person.bubble.dx=e.clientX-r.left-dragging.person.x-dragOff.x;dragging.person.bubble.dy=e.clientY-r.top-dragging.person.y-dragOff.y;}
    renderP();
  };
  const onMouseUp=()=>{dragging=null;};
  const onTouchMove=e=>{
    if(!dragging)return; const t=e.touches[0];
    const r=$("vmStage").getBoundingClientRect();
    if(dragging.type==="person"){dragging.person.x=t.clientX-r.left-dragOff.x;dragging.person.y=t.clientY-r.top-dragOff.y;}
    else if(dragging.type==="bubble"&&dragging.person.bubble){dragging.person.bubble.dx=t.clientX-r.left-dragging.person.x-dragOff.x;dragging.person.bubble.dy=t.clientY-r.top-dragging.person.y-dragOff.y;}
    renderP();
  };
  const onTouchEnd=()=>{dragging=null;};
  const onKeyDown=e=>{
    const tag=(document.activeElement&&document.activeElement.tagName)||"";
    if(tag==="INPUT"||tag==="TEXTAREA"||tag==="SELECT")return;
    const mod=e.ctrlKey||e.metaKey, k=e.key.toLowerCase();
    if(mod&&k==="z"){e.preventDefault();e.shiftKey?redo():undo();return;}
    if(mod&&k==="y"){e.preventDefault();redo();return;}
    if(!selected)return;
    if(mod&&k==="d"){e.preventDefault();dupSel();return;}
    if(k==="f"&&!mod){e.preventDefault();flipSel();return;}
    if(e.key==="["){ e.preventDefault();sendBack();return;}
    if(e.key==="]"){ e.preventDefault();bringFront();return;}
    const step=e.shiftKey?12:4;
    if(e.key==="Delete"||e.key==="Backspace"){e.preventDefault();deleteSel();}
    else if(e.key==="ArrowLeft"){e.preventDefault();selected.x-=step;renderP();}
    else if(e.key==="ArrowRight"){e.preventDefault();selected.x+=step;renderP();}
    else if(e.key==="ArrowUp"){e.preventDefault();selected.y-=step;renderP();}
    else if(e.key==="ArrowDown"){e.preventDefault();selected.y+=step;renderP();}
  };
  const onResize=()=>render();

  document.addEventListener("mousemove",onMouseMove);
  document.addEventListener("mouseup",onMouseUp);
  document.addEventListener("touchmove", onTouchMove, {passive:false});
  document.addEventListener("touchend",onTouchEnd);
  document.addEventListener("keydown",onKeyDown);
  window.addEventListener("resize",onResize);

  $("vmStage")?.addEventListener("mousedown",e=>{
    if(e.target.tagName==="image"||e.target.tagName==="svg"||e.target.id==="vmStage"){selected=null;renderP();buildPanel();}
  });

  $("vmFlipBtn")?.addEventListener("click",flipSel);
  $("vmDupBtn")?.addEventListener("click",dupSel);
  $("vmDelBtn")?.addEventListener("click",deleteSel);

  buildBgStrip();
  buildLeftBar();
  setTimeout(()=>{render();flushSnapshot();updateHistBtns();},100);

  function exportPNG(callback) {
    const stageEl=$("vmStage"); if(!stageEl)return;
    const W=stageEl.clientWidth||700, H=stageEl.clientHeight||500, S=2;
    const out=document.createElement("canvas"); out.width=W*S; out.height=H*S;
    const ctx=out.getContext("2d");
    const bgC=document.createElement("canvas"); bgC.width=W*S; bgC.height=H*S;
    drawScene(currentSceneId,bgC,true); ctx.drawImage(bgC,0,0);
    if(!people.length){callback(out.toDataURL("image/png"));return;}
    const sorted=[...people].sort((a,b)=>a.z-b.z);
    const svgStr=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W*S}" height="${H*S}">`
      +sorted.map(p=>personSVG(p,false)).join("")
      +sorted.map(p=>bubbleSVG(p,false)).join("")
      +"</svg>";
    const img=new Image();
    img.onload=()=>{ctx.drawImage(img,0,0,W*S,H*S);callback(out.toDataURL("image/png"));};
    img.onerror=()=>callback(null);
    img.src="data:image/svg+xml;charset=utf-8,"+encodeURIComponent(svgStr);
  }

  return {
    undo, redo,
    clear() { flushSnapshot(); people=[]; selected=null; renderP(); buildPanel(); },
    addBubble,
    exportPNG,
    getState() { return {sceneId:currentSceneId,people:JSON.parse(JSON.stringify(people)),nextId}; },
    setState(state) {
      currentSceneId=state.sceneId||currentSceneId;
      people=state.people||[];
      nextId=state.nextId||(Math.max(0,...people.map(p=>p.uid))+1);
      selected=null; syncBgStrip(); render(); buildPanel();
    },
    cleanup() {
      document.removeEventListener("mousemove",onMouseMove);
      document.removeEventListener("mouseup",onMouseUp);
      document.removeEventListener("touchmove",onTouchMove);
      document.removeEventListener("touchend",onTouchEnd);
      document.removeEventListener("keydown",onKeyDown);
      window.removeEventListener("resize",onResize);
    },
  };
}
</script>

<template>
  <teleport to="body">
    <div class="vm-overlay" role="dialog" aria-modal="true" aria-label="Vignette">

      <header class="vm-header">
        <div class="vm-hd-left">
          <span class="vm-brand">Vignette</span>
          <span class="vm-hd-sep"/>
          <button class="vm-hbtn" :disabled="!canUndo" @click="undoMaker" title="Undo (Ctrl+Z)">↩ Undo</button>
          <button class="vm-hbtn" :disabled="!canRedo" @click="redoMaker" title="Redo (Ctrl+Shift+Z)">↪ Redo</button>
          <button class="vm-hbtn vm-hbtn--warn" @click="clearMaker">Clear all</button>
        </div>

        <div class="vm-scene-strip-wrap">
          <div id="vmBgStrip" class="vm-bg-strip"></div>
        </div>

        <div class="vm-hd-right">
          <button class="vm-hbtn" @click="openScenesPanel">
            My Scenes{{ savedScenes.length ? ` (${savedScenes.length})` : "" }}
          </button>
          <button class="vm-hbtn vm-hbtn--save" @click="promptSave" :disabled="savingScene">
            {{ savingScene ? "Saving..." : "Save Progress" }}
          </button>
          <span class="vm-hd-sep"/>
          <button class="vm-hbtn vm-hbtn--insert" @click="insertIntoFrame">
            ✓ Use This Image
          </button>
          <button class="vm-hbtn vm-hbtn--close" @click="$emit('close')" title="Close">✕</button>
        </div>
      </header>

      <div class="vm-body">

        <aside class="vm-cast">
          <div class="vm-cast-lbl">Cast</div>
          <div id="vmCharPick"></div>
        </aside>

        <div class="vm-stage-wrap">
          <svg id="vmStage" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <clipPath id="vmStageClip"><rect id="vmStageClipRect" width="100%" height="100%"/></clipPath>
            </defs>
            <g id="vmBgLayer" clip-path="url(#vmStageClip)"></g>
            <g id="vmPeople"></g>
            <g id="vmBubbles"></g>
            <text id="vmHint" x="50%" y="52%" text-anchor="middle" dominant-baseline="middle"
                  font-size="13" fill="rgba(0,0,0,.18)" font-family="sans-serif">
              ← Add characters from the left panel
            </text>
          </svg>
          <div class="vm-stage-tools">
            <button class="vm-stage-btn" @click="addBubbleFn">Add Bubble</button>
          </div>
        </div>

        <aside class="vm-right-panel">
          <div class="vm-rph" id="vmRph" style="display:none">
            <span class="vm-rph-name" id="vmRphName"></span>
            <button class="vm-rph-btn" id="vmFlipBtn">Flip</button>
            <button class="vm-rph-btn" id="vmDupBtn">Copy</button>
            <button class="vm-rph-del" id="vmDelBtn">✕</button>
          </div>
          <div class="vm-rpb" id="vmRpb">
            <div class="vm-rp-empty">Click a character on stage<br>to edit them</div>
          </div>
        </aside>
      </div>

      <transition name="vm-slide">
        <div v-if="scenesPanelOpen" class="vm-scenes-backdrop" @click.self="scenesPanelOpen = false">
          <div class="vm-scenes-panel">
            <div class="vm-scenes-hdr">
              <h3>My Saved Scenes</h3>
              <button class="vm-scenes-close" @click="scenesPanelOpen = false">✕</button>
            </div>
            <div v-if="loadingScenes" class="vm-scenes-empty">Loading...</div>
            <div v-else-if="!savedScenes.length" class="vm-scenes-empty">
              No saved scenes yet.<br>Click <strong>Save Progress</strong> to save your work and resume later.
            </div>
            <div v-else class="vm-scenes-list">
              <div v-for="scene in savedScenes" :key="scene.id" class="vm-scene-item">
                <div class="vm-scene-meta">
                  <strong class="vm-scene-name">{{ scene.name }}</strong>
                  <span class="vm-scene-date">{{ formatDate(scene.updatedAt) }}</span>
                </div>
                <div class="vm-scene-btns">
                  <button class="vm-scene-btn" @click="saveCurrentToExisting(scene)" title="Overwrite with current stage">Update</button>
                  <button class="vm-scene-btn vm-scene-btn--load" @click="loadScene(scene)">Load</button>
                  <button class="vm-scene-btn vm-scene-btn--del" @click="removeScene(scene.id)">✕</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </transition>

      <transition name="vm-fade">
        <div v-if="saveNameDialogOpen" class="vm-dialog-backdrop" @click.self="saveNameDialogOpen = false">
          <div class="vm-save-dialog">
            <h3>Save Scene</h3>
            <input
              v-model="saveNameInput"
              class="vm-save-input"
              placeholder="Scene name..."
              maxlength="80"
              @keydown.enter="confirmSave"
              @keydown.esc="saveNameDialogOpen = false"
              autofocus
            />
            <div class="vm-save-actions">
              <button class="vm-save-cancel" @click="saveNameDialogOpen = false">Cancel</button>
              <button class="vm-save-confirm" @click="confirmSave" :disabled="savingScene">
                {{ savingScene ? "Saving..." : "Save" }}
              </button>
            </div>
          </div>
        </div>
      </transition>

    </div>
  </teleport>
</template>

<style>
.vm-overlay {
  position: fixed; inset: 0; z-index: 9999;
  display: flex; flex-direction: column;
  background: var(--bg);
  font-family: 'Avenir Next', 'Segoe UI', system-ui, sans-serif;
  color: var(--text);
}

.vm-header {
  display: flex; align-items: center; gap: 8px;
  padding: 0 16px; flex-shrink: 0;
  background: #5B1928;
  border-bottom: 1px solid rgba(255,255,255,.10);
  min-height: 56px; flex-wrap: nowrap; overflow: hidden;
}

.vm-hd-left  { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.vm-hd-right { display: flex; align-items: center; gap: 6px; flex-shrink: 0; margin-left: auto; }

.vm-brand {
  font-family: Times, 'Times New Roman', Georgia, serif;
  font-size: 1.12rem; font-weight: 800;
  color: #fff; white-space: nowrap;
  line-height: 1;
}

.vm-hd-sep { width: 1px; height: 20px; background: rgba(255,255,255,.18); flex-shrink: 0; }

.vm-scene-strip-wrap { flex: 1; overflow: hidden; min-width: 0; }
.vm-bg-strip {
  display: flex; gap: 5px; padding: 4px 4px;
  overflow-x: auto; height: 100%; align-items: center;
}
.vm-bg-strip::-webkit-scrollbar { height: 3px; }
.vm-bg-strip::-webkit-scrollbar-thumb { background: rgba(255,255,255,.25); border-radius: 2px; }
.vm-bg-thumb {
  flex-shrink: 0; width: 66px; cursor: pointer;
  border-radius: 8px; overflow: hidden;
  border: 2px solid rgba(255,255,255,.18);
  background: rgba(255,255,255,.08);
  transition: border-color .14s, transform .1s;
}
.vm-bg-thumb:hover { border-color: rgba(255,255,255,.55); transform: translateY(-1px); }
.vm-bg-thumb.on  { border-color: #fff; }
.vm-bg-thumb canvas { display: block; width: 66px; height: 42px; }
.vm-bg-thumb span {
  display: block; font-size: 7.5px; font-weight: 600;
  color: rgba(255,255,255,.7); text-align: center;
  padding: 2px 2px 3px; background: rgba(0,0,0,.25);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.vm-bg-thumb.on span { color: #fff; background: rgba(255,255,255,.18); }

.vm-hbtn {
  display: inline-flex; align-items: center; gap: 5px;
  min-height: 36px; padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(255,255,255,.22);
  background: rgba(255,255,255,.10);
  color: rgba(255,255,255,.88);
  font-size: 0.78rem; font-weight: 600;
  cursor: pointer; white-space: nowrap;
  transition: background .12s, border-color .12s, color .12s;
}
.vm-hbtn:hover { background: rgba(255,255,255,.18); border-color: rgba(255,255,255,.40); color: #fff; }
.vm-hbtn:disabled { opacity: .35; cursor: default; }

.vm-hbtn--warn { color: #ffbdbd; border-color: rgba(255,189,189,.30); }
.vm-hbtn--warn:hover { background: rgba(255,100,100,.18); border-color: rgba(255,189,189,.55); color: #ffcaca; }

.vm-hbtn--save { color: #b8d4a8; border-color: rgba(184,212,168,.32); }
.vm-hbtn--save:hover { background: rgba(184,212,168,.18); border-color: rgba(184,212,168,.55); color: #d4e5ca; }

.vm-hbtn--insert {
  background: var(--primary); color: #fff;
  border-color: var(--primary);
  font-size: 0.82rem; font-weight: 700;
  padding: 0 18px;
}
.vm-hbtn--insert:hover { background: var(--primary-strong); border-color: var(--primary-strong); }

.vm-hbtn--close {
  width: 36px; height: 36px; padding: 0;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 16px; border-radius: 12px;
}

.vm-body { display: flex; flex: 1; overflow: hidden; }

.vm-cast {
  width: 76px;
  background: var(--surface-alt);
  border-right: 1px solid var(--border);
  display: flex; flex-direction: column; align-items: center;
  padding: 8px 4px; gap: 5px; overflow-y: auto; flex-shrink: 0;
}
.vm-cast::-webkit-scrollbar { width: 3px; }
.vm-cast::-webkit-scrollbar-thumb { background: var(--accent-warm); border-radius: 2px; }
.vm-cast-lbl {
  font-size: 0.62rem; font-weight: 800; letter-spacing: .08em;
  text-transform: uppercase; color: var(--text-soft);
  padding-bottom: 2px;
}
.vm-char-card {
  width: 62px; border-radius: 10px;
  border: 1.5px solid var(--border);
  background: var(--surface); cursor: pointer;
  display: flex; flex-direction: column; align-items: center;
  padding: 4px 2px 3px; gap: 2px; flex-shrink: 0;
  transition: border-color .14s, transform .1s, box-shadow .14s;
}
.vm-char-card:hover {
  border-color: var(--primary);
  transform: scale(1.04);
  box-shadow: 0 3px 10px rgba(30,8,18,.09);
}
.vm-char-card span { font-size: 0.62rem; font-weight: 700; color: var(--text-soft); text-align: center; }

.vm-stage-wrap { flex: 1; position: relative; overflow: hidden; background: var(--accent-cool); }
.vm-stage-wrap svg { width: 100%; height: 100%; display: block; overflow: hidden; shape-rendering: geometricPrecision; }
.vm-stage-tools { position: absolute; top: 10px; right: 12px; z-index: 4; }
.vm-stage-btn {
  display: inline-flex; align-items: center; gap: 6px;
  min-height: 36px; padding: 0 14px;
  font-size: 0.78rem; font-weight: 700;
  border-radius: 12px; border: 1px solid var(--border);
  background: rgba(255,255,255,.92); color: var(--text-soft);
  backdrop-filter: blur(6px);
  box-shadow: 0 2px 10px rgba(30,8,18,.08);
  transition: border-color .12s, color .12s, background .12s;
}
.vm-stage-btn:hover { border-color: var(--primary); color: var(--primary); background: #fff; }

.vm-right-panel {
  width: 248px; background: var(--surface);
  border-left: 1px solid var(--border);
  display: flex; flex-direction: column; overflow: hidden; flex-shrink: 0;
}
.vm-rph {
  padding: 9px 12px 8px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-alt);
  display: flex; align-items: center; gap: 5px; flex-shrink: 0;
}
.vm-rph-name {
  font-size: 0.88rem; font-weight: 700; flex: 1;
  color: var(--text);
}
.vm-rph-btn {
  font-size: 0.72rem; font-weight: 600; cursor: pointer;
  padding: 4px 10px; border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--surface); color: var(--text-soft);
  transition: border-color .12s, color .12s;
}
.vm-rph-btn:hover { border-color: var(--primary); color: var(--primary); }
.vm-rph-del {
  font-size: 0.72rem; color: var(--danger); cursor: pointer;
  padding: 4px 10px; border-radius: 8px;
  border: 1px solid var(--accent-warm);
  background: transparent;
  transition: background .12s;
}
.vm-rph-del:hover { background: var(--accent-warm); }
.vm-rpb {
  flex: 1; overflow-y: auto;
  padding: 6px 12px 20px;
  display: flex; flex-direction: column; gap: 2px;
}
.vm-rpb::-webkit-scrollbar { width: 4px; }
.vm-rpb::-webkit-scrollbar-thumb { background: var(--accent-warm); border-radius: 3px; }
.vm-rp-empty {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: var(--text-soft);
  font-size: 0.82rem; text-align: center;
  padding: 20px; line-height: 1.7;
}

.vm-sec {
  font-size: 0.62rem; font-weight: 800;
  color: var(--text-soft); text-transform: uppercase;
  letter-spacing: .08em; padding: 8px 0 3px;
}
.vm-sub-lbl { font-size: 0.68rem; color: var(--text-soft); padding: 2px 0 1px; }
.vm-cr { display: flex; align-items: center; gap: 5px; margin-bottom: 1px; }
.vm-cr label { font-size: 0.7rem; color: var(--text-soft); width: 62px; flex-shrink: 0; font-weight: 600; }
.vm-cr input[type=range] { flex: 1; min-width: 0; accent-color: var(--primary); height: 14px; }
.vm-v { font-size: 0.7rem; font-weight: 700; width: 26px; text-align: right; color: var(--primary); }

.vm-sw-row { display: flex; gap: 4px; flex-wrap: wrap; }
.vm-sw {
  width: 20px; height: 20px; border-radius: 50%; cursor: pointer;
  border: 2px solid transparent; transition: transform .12s;
}
.vm-sw:hover { transform: scale(1.2); }
.vm-sw.on { border-color: var(--primary) !important; box-shadow: 0 0 0 1.5px #fff, 0 0 0 3px var(--primary); }
.vm-sw--sm { width: 16px; height: 16px; }

.vm-opt-row { display: flex; gap: 4px; flex-wrap: wrap; }
.vm-opt {
  padding: 4px 9px; border-radius: 8px;
  border: 1.5px solid var(--border);
  font-size: 0.72rem; cursor: pointer;
  background: var(--surface); color: var(--text-soft);
  font-weight: 600; transition: all .1s;
}
.vm-opt:hover  { border-color: var(--primary); color: var(--primary); background: var(--accent-cool); }
.vm-opt.on     { border-color: var(--primary); color: var(--primary); background: var(--accent-green); font-weight: 700; }

.vm-dv { height: 1px; background: var(--border); margin: 5px 0; }

.vm-layer-row { display: flex; gap: 5px; padding: 0 0 3px; }
.vm-layer-btn {
  flex: 1; font-size: 0.72rem; font-weight: 600; padding: 5px 0;
  border-radius: 8px; border: 1px solid var(--border);
  background: var(--surface); cursor: pointer; color: var(--text-soft);
  transition: border-color .12s, color .12s, background .12s;
}
.vm-layer-btn:hover { border-color: var(--primary); color: var(--primary); background: var(--accent-cool); }

.vm-mini-btn {
  font-size: 0.72rem; font-weight: 700; padding: 6px 12px;
  border-radius: 8px; border: 1px solid var(--border);
  background: var(--surface); color: var(--primary); cursor: pointer;
  transition: border-color .12s, background .12s;
}
.vm-mini-btn:hover { border-color: var(--primary); background: var(--accent-cool); }
.vm-mini-btn--warn { color: var(--danger); border-color: var(--accent-warm); }
.vm-mini-btn--warn:hover { background: var(--accent-warm); border-color: var(--danger); }

.vm-adv-toggle {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; padding: 6px 2px;
  border: none; background: none; cursor: pointer;
  font-size: 0.7rem; font-weight: 700; color: var(--text-soft);
}
.vm-adv-toggle:hover { color: var(--primary); }
.vm-adv-chevron { transition: transform .12s; }
.vm-adv-chevron.open { transform: rotate(180deg); }
.vm-adv-body { padding-top: 2px; }

.vm-hint-kbd {
  font-size: 0.65rem; color: var(--text-soft);
  text-align: center; padding-top: 10px;
  line-height: 1.8; opacity: .75;
}
.vm-textbox {
  width: 100%; min-height: 72px; resize: vertical;
  border: 1px solid var(--border); border-radius: 10px;
  background: var(--surface-alt); padding: 8px 10px;
  font: 500 0.82rem/1.5 'Avenir Next','Segoe UI',system-ui,sans-serif;
  color: var(--text); outline: none;
}
.vm-textbox:focus { border-color: var(--primary); background: var(--surface); box-shadow: 0 0 0 3px var(--accent-cool); }
.vm-bubble-tip {
  background: var(--surface-alt); border: 1px dashed var(--border);
  border-radius: 10px; padding: 7px 10px;
  font-size: 0.72rem; line-height: 1.6; color: var(--text-soft);
}

.vm-scenes-backdrop { position: absolute; inset: 0; z-index: 20; display: flex; justify-content: flex-end; }
.vm-scenes-panel {
  width: 340px; height: 100%;
  background: var(--surface);
  border-left: 1px solid var(--border);
  display: flex; flex-direction: column;
  box-shadow: -8px 0 32px rgba(30,8,18,.14);
}
.vm-scenes-hdr {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid var(--border);
  background: #5B1928; flex-shrink: 0;
}
.vm-scenes-hdr h3 { font-size: 0.95rem; font-weight: 800; margin: 0; color: #fff; letter-spacing: -.01em; }
.vm-scenes-close {
  width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.22);
  border-radius: 8px; cursor: pointer; color: rgba(255,255,255,.80); font-size: 14px;
}
.vm-scenes-close:hover { background: rgba(255,255,255,.22); color: #fff; }
.vm-scenes-empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  text-align: center; padding: 28px; font-size: 0.84rem; color: var(--text-soft); line-height: 1.7;
}
.vm-scenes-list { flex: 1; overflow-y: auto; padding: 10px; display: flex; flex-direction: column; gap: 7px; }
.vm-scene-item {
  padding: 11px 13px; border: 1px solid var(--border); border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, var(--surface-alt) 100%);
  box-shadow: 0 2px 8px rgba(30,8,18,.04);
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
}
.vm-scene-meta { flex: 1; min-width: 0; }
.vm-scene-name { display: block; font-size: 0.84rem; font-weight: 700; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.vm-scene-date { font-size: 0.7rem; color: var(--text-soft); }
.vm-scene-btns { display: flex; gap: 5px; flex-shrink: 0; }
.vm-scene-btn {
  font-size: 0.7rem; font-weight: 700; padding: 5px 11px;
  border-radius: 8px; border: 1px solid var(--border);
  background: var(--surface); color: var(--text-soft); cursor: pointer;
  transition: border-color .12s, color .12s;
}
.vm-scene-btn:hover { border-color: var(--primary); color: var(--primary); }
.vm-scene-btn--load { background: var(--primary); color: #fff; border-color: var(--primary); }
.vm-scene-btn--load:hover { background: var(--primary-strong); border-color: var(--primary-strong); }
.vm-scene-btn--del { color: var(--danger); border-color: var(--accent-warm); }
.vm-scene-btn--del:hover { background: var(--accent-warm); }

.vm-dialog-backdrop {
  position: absolute; inset: 0; z-index: 30;
  background: rgba(30,8,18,.50);
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(4px);
}
.vm-save-dialog {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px; width: 340px;
  box-shadow: 0 20px 60px rgba(30,8,18,.28);
}
.vm-save-dialog h3 { font-size: 1rem; font-weight: 800; margin: 0 0 14px; color: var(--text); letter-spacing: -.01em; }
.vm-save-input {
  width: 100%; padding: 11px 13px;
  border: 1px solid var(--border); border-radius: 12px;
  background: var(--surface-alt);
  font: 600 0.9rem/1 'Avenir Next','Segoe UI',system-ui,sans-serif;
  color: var(--text); outline: none;
}
.vm-save-input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px var(--accent-cool); background: var(--surface); }
.vm-save-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
.vm-save-cancel {
  min-height: 38px; padding: 0 16px; border-radius: 10px;
  background: var(--surface); border: 1px solid var(--border);
  color: var(--text-soft); font-size: 0.84rem; font-weight: 600; cursor: pointer;
  transition: border-color .12s, color .12s;
}
.vm-save-cancel:hover { border-color: var(--primary); color: var(--primary); }
.vm-save-confirm {
  min-height: 38px; padding: 0 20px; border-radius: 10px;
  background: var(--primary); color: #fff;
  border: none; font-size: 0.84rem; font-weight: 700; cursor: pointer;
  transition: background .12s;
}
.vm-save-confirm:hover { background: var(--primary-strong); }
.vm-save-confirm:disabled { opacity: .45; cursor: default; }

.vm-slide-enter-active, .vm-slide-leave-active { transition: opacity .18s, transform .18s; }
.vm-slide-enter-from, .vm-slide-leave-to { opacity: 0; transform: translateX(16px); }
.vm-fade-enter-active,  .vm-fade-leave-active  { transition: opacity .14s; }
.vm-fade-enter-from,    .vm-fade-leave-to      { opacity: 0; }

.vm-person          { cursor: grab; }
.vm-person:active   { cursor: grabbing; }
</style>
