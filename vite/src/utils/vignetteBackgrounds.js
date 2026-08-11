export const SCENES = [
  {id: "hearth", label: "Fireplace", draw: drawHearth},
  {id: "porch", label: "Porch at Dusk", draw: drawPorch},
  {id: "kitchen", label: "Family Kitchen", draw: drawKitchen},
  {id: "atelier", label: "Weaving Room", draw: drawAtelier},
  {id: "market", label: "Village Market", draw: drawMarket},
  {id: "garden", label: "Community Garden", draw: drawGarden},
  {id: "field", label: "Harvest Field", draw: drawField},
  {id: "riverside", label: "Riverside", draw: drawRiverside},
  {id: "plaza", label: "Town Plaza", draw: drawPlaza},
  {id: "forest", label: "Sacred Grove", draw: drawForest},
  {id: "classroom", label: "Under the Tree", draw: drawClassroom},
  {id: "night", label: "Night Ceremony", draw: drawNight},
  {id: "celebration", label: "Celebration", draw: drawCelebration},
];

export function drawScene(id, canvas, forStage) {
  const scene = SCENES.find((item) => item.id === id);
  if (scene) scene.draw(canvas, forStage);
}

function bgCtx(canvas) {
  return canvas.getContext("2d");
}

function seeded01(seed, i) {
  const x = Math.sin(seed * 1000 + i * 127.1) * 43758.5453123;
  return x - Math.floor(x);
}

function drawRoundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  if (ctx.roundRect) {
    ctx.roundRect(x, y, w, h, r);
  } else {
    ctx.rect(x, y, w, h);
  }
}

function drawHearth(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const wall = ctx.createLinearGradient(0, 0, 0, H);
  wall.addColorStop(0, "#2C1A12");
  wall.addColorStop(.55, "#3A2416");
  wall.addColorStop(1, "#24140C");
  ctx.fillStyle = wall; ctx.fillRect(0, 0, W, H);
  const glow = ctx.createRadialGradient(W * .5, H * .63, 8, W * .5, H * .63, W * .55);
  glow.addColorStop(0, "rgba(255,150,50,.34)");
  glow.addColorStop(.5, "rgba(230,95,30,.13)");
  glow.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "#2A160C"; ctx.fillRect(0, H * .66, W, H * .34);
  if (fs) {
    ctx.strokeStyle = "rgba(120,75,45,.35)"; ctx.lineWidth = 2;
    for (let y = H * .70; y < H; y += H * .055) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
    ctx.fillStyle = "rgba(168,74,52,.5)";
    ctx.beginPath(); ctx.ellipse(W * .5, H * .86, W * .20, H * .055, 0, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "rgba(230,190,120,.35)"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.ellipse(W * .5, H * .86, W * .16, H * .042, 0, 0, Math.PI * 2); ctx.stroke();
    ctx.beginPath(); ctx.ellipse(W * .5, H * .86, W * .10, H * .028, 0, 0, Math.PI * 2); ctx.stroke();
  }
  const fx = W * .29, fy = H * .32, fw = W * .42, fh = H * .42;
  ctx.fillStyle = "#5A3B2A"; ctx.fillRect(fx, fy, fw, fh);
  ctx.fillStyle = "#6B4934"; ctx.fillRect(fx - fw * .05, fy - H * .045, fw * 1.1, H * .06);
  ctx.fillStyle = "#3A2216"; ctx.fillRect(fx + fw * .08, fy + fh * .18, fw * .84, fh * .68);
  const cx = W * .5, base = fy + fh * .72;
  ctx.fillStyle = "#2B160D";
  drawRoundRect(ctx, fx + fw * .16, fy + fh * .26, fw * .68, fh * .55, fs ? 12 : 4);
  ctx.fill();
  ctx.strokeStyle = "#6A3A18"; ctx.lineWidth = fs ? 8 : 2.5; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(cx - fw * .16, base); ctx.lineTo(cx + fw * .18, base + H * .025); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(cx - fw * .18, base + H * .035); ctx.lineTo(cx + fw * .14, base + H * .005); ctx.stroke();
  ctx.fillStyle = "rgba(232,72,22,.95)";
  ctx.beginPath();
  ctx.moveTo(cx, base - H * .15);
  ctx.bezierCurveTo(cx - fw * .16, base - H * .07, cx - fw * .13, base + H * .035, cx, base + H * .02);
  ctx.bezierCurveTo(cx + fw * .13, base + H * .035, cx + fw * .16, base - H * .07, cx, base - H * .15);
  ctx.closePath(); ctx.fill();
  ctx.fillStyle = "rgba(255,164,38,.88)";
  ctx.beginPath();
  ctx.moveTo(cx, base - H * .11);
  ctx.bezierCurveTo(cx - fw * .09, base - H * .045, cx - fw * .07, base + H * .02, cx, base + H * .01);
  ctx.bezierCurveTo(cx + fw * .07, base + H * .02, cx + fw * .09, base - H * .045, cx, base - H * .11);
  ctx.closePath(); ctx.fill();
  ctx.fillStyle = "rgba(255,228,118,.68)";
  ctx.beginPath();
  ctx.moveTo(cx, base - H * .07);
  ctx.bezierCurveTo(cx - fw * .035, base - H * .025, cx - fw * .025, base + H * .005, cx, base);
  ctx.bezierCurveTo(cx + fw * .025, base + H * .005, cx + fw * .035, base - H * .025, cx, base - H * .07);
  ctx.closePath(); ctx.fill();
  if (fs) {
    [[.36], [.63]].forEach(([x]) => {
      ctx.fillStyle = "#D8C8A8"; ctx.fillRect(W * x - 4, fy - H * .10, 8, H * .055);
      ctx.fillStyle = "rgba(255,200,90,.9)";
      ctx.beginPath(); ctx.ellipse(W * x, fy - H * .115, 3.5, 6, 0, 0, Math.PI * 2); ctx.fill();
      const cg = ctx.createRadialGradient(W * x, fy - H * .115, 0, W * x, fy - H * .115, 26);
      cg.addColorStop(0, "rgba(255,220,130,.25)"); cg.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = cg; ctx.fillRect(W * x - 28, fy - H * .115 - 28, 56, 56);
    });
    ctx.fillStyle = "rgba(255,225,160,.09)";
    [[.16, .22, .055], [.84, .24, .05]].forEach(([x, y, r]) => {
      ctx.beginPath(); ctx.arc(W * x, H * y, W * r, 0, Math.PI * 2); ctx.fill();
    });
    [[.14, .20], [.85, .22]].forEach(([x, y]) => {
      ctx.strokeStyle = "#8A5A30"; ctx.lineWidth = 3;
      ctx.strokeRect(W * x - W * .028, H * y - H * .045, W * .056, H * .09);
      ctx.fillStyle = "rgba(220,190,150,.30)";
      ctx.fillRect(W * x - W * .022, H * y - H * .036, W * .044, H * .072);
    });
    ctx.strokeStyle = "rgba(120,80,55,.32)"; ctx.lineWidth = 1.5;
    for (let x = fx; x < fx + fw; x += fw / 4) {
      ctx.beginPath(); ctx.moveTo(x, fy); ctx.lineTo(x, fy + fh); ctx.stroke();
    }
  }
}

function drawPorch(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H * .62);
  sky.addColorStop(0, "#B0526A");
  sky.addColorStop(.42, "#D8785A");
  sky.addColorStop(.78, "#F0A860");
  sky.addColorStop(1, "#F6C878");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H * .62);
  ctx.fillStyle = "rgba(255,220,140,.85)";
  ctx.beginPath(); ctx.arc(W * .68, H * .44, fs ? 30 : 9, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "rgba(255,220,140,.20)";
  ctx.beginPath(); ctx.arc(W * .68, H * .44, fs ? 62 : 18, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#7A4638";
  ctx.beginPath(); ctx.moveTo(0, H * .55); ctx.bezierCurveTo(W * .2, H * .48, W * .45, H * .58, W * .7, H * .52); ctx.bezierCurveTo(W * .85, H * .48, W * .95, H * .54, W, H * .51); ctx.lineTo(W, H * .62); ctx.lineTo(0, H * .62); ctx.closePath(); ctx.fill();
  ctx.fillStyle = "#5A3028";
  ctx.beginPath(); ctx.moveTo(0, H * .60); ctx.bezierCurveTo(W * .3, H * .55, W * .6, H * .63, W, H * .57); ctx.lineTo(W, H * .65); ctx.lineTo(0, H * .65); ctx.closePath(); ctx.fill();
  if (fs) {
    ctx.strokeStyle = "rgba(60,25,15,.85)"; ctx.lineWidth = 1.8;
    [[.10, .40], [.16, .43], [.87, .38]].forEach(([x, y]) => {
      ctx.beginPath(); ctx.moveTo(W * x - 7, H * y); ctx.quadraticCurveTo(W * x - 1, H * y - 4, W * x, H * y); ctx.quadraticCurveTo(W * x + 1, H * y - 4, W * x + 7, H * y); ctx.stroke();
    });
  }
  const floor = ctx.createLinearGradient(0, H * .62, 0, H);
  floor.addColorStop(0, "#9A6438");
  floor.addColorStop(1, "#6A3E20");
  ctx.fillStyle = floor; ctx.fillRect(0, H * .62, W, H * .38);
  if (fs) {
    ctx.strokeStyle = "rgba(60,30,12,.35)"; ctx.lineWidth = 2;
    for (let i = 0; i < 7; i++) {
      const t = i / 6;
      ctx.beginPath(); ctx.moveTo(W * (.14 + t * .72), H * .62); ctx.lineTo(W * t, H); ctx.stroke();
    }
    ctx.beginPath(); ctx.moveTo(0, H * .74); ctx.lineTo(W, H * .74); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, H * .88); ctx.lineTo(W, H * .88); ctx.stroke();
  }
  ctx.fillStyle = "#4A2A18"; ctx.fillRect(0, H * .47, W, H * .022);
  ctx.fillRect(0, H * .60, W, H * .018);
  if (fs) {
    for (let x = W * .03; x < W; x += W * .062) {
      ctx.fillStyle = "#5A3420"; ctx.fillRect(x - 3, H * .482, 6, H * .12);
    }
  }
  ctx.fillStyle = "#3E2212";
  ctx.fillRect(W * .055, H * .05, W * .028, H * .60);
  ctx.fillRect(W * .917, H * .05, W * .028, H * .60);
  ctx.fillRect(0, 0, W, H * .075);
  ctx.fillStyle = "#2E180C"; ctx.fillRect(0, H * .062, W, H * .018);
  if (fs) {
    ctx.strokeStyle = "#2A1810"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(W * .24, H * .08); ctx.lineTo(W * .24, H * .15); ctx.stroke();
    const lg = ctx.createRadialGradient(W * .24, H * .20, 0, W * .24, H * .20, 60);
    lg.addColorStop(0, "rgba(255,205,110,.30)"); lg.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = lg; ctx.fillRect(W * .24 - 64, H * .20 - 64, 128, 128);
    ctx.fillStyle = "#F2B44E";
    ctx.beginPath(); ctx.ellipse(W * .24, H * .20, 11, 15, 0, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "#3A2010"; ctx.lineWidth = 2; ctx.stroke();
    ctx.beginPath(); ctx.moveTo(W * .24, H * .155); ctx.lineTo(W * .24, H * .245); ctx.stroke();
    ctx.strokeStyle = "#2A1810"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(W * .80, H * .08); ctx.lineTo(W * .80, H * .14); ctx.stroke();
    ctx.fillStyle = "#8A4A2A";
    ctx.beginPath(); ctx.moveTo(W * .775, H * .14); ctx.lineTo(W * .825, H * .14); ctx.lineTo(W * .815, H * .185); ctx.lineTo(W * .785, H * .185); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = "#4E7A38"; ctx.lineWidth = 2.4; ctx.lineCap = "round";
    [[-.02, .09], [0, .11], [.02, .095], [-.032, .06], [.032, .065]].forEach(([dx, dy]) => {
      ctx.beginPath(); ctx.moveTo(W * .80, H * .145); ctx.quadraticCurveTo(W * (.80 + dx * 1.4), H * .16, W * (.80 + dx), H * (.14 + dy)); ctx.stroke();
    });
    ctx.strokeStyle = "#33190C"; ctx.lineWidth = 4; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(W * .115, H * .52); ctx.lineTo(W * .115, H * .70); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(W * .115, H * .66); ctx.lineTo(W * .175, H * .665); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(W * .175, H * .665); ctx.lineTo(W * .178, H * .72); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(W * .095, H * .735); ctx.quadraticCurveTo(W * .148, H * .765, W * .198, H * .73); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(W * .11, H * .70); ctx.lineTo(W * .105, H * .74); ctx.moveTo(W * .178, H * .70); ctx.lineTo(W * .185, H * .735); ctx.stroke();
    ctx.fillStyle = "#B8542E"; ctx.fillRect(W * .225, H * .705, W * .018, H * .03);
    ctx.strokeStyle = "#B8542E"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(W * .248, H * .72, 5, -.6, 1.6); ctx.stroke();
  }
}

function drawKitchen(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const wall = ctx.createLinearGradient(0, 0, 0, H * .60);
  wall.addColorStop(0, "#F4E7CE");
  wall.addColorStop(1, "#EAD9B8");
  ctx.fillStyle = wall; ctx.fillRect(0, 0, W, H);
  const floor = ctx.createLinearGradient(0, H * .60, 0, H);
  floor.addColorStop(0, "#C9A57A");
  floor.addColorStop(1, "#A67C56");
  ctx.fillStyle = floor; ctx.fillRect(0, H * .60, W, H * .40);
  if (fs) {
    ctx.strokeStyle = "rgba(160,125,90,.16)"; ctx.lineWidth = 1;
    for (let y = 0; y < H * .60; y += H * .06) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
    ctx.strokeStyle = "rgba(120,85,55,.25)"; ctx.lineWidth = 1.5;
    for (let i = 0; i < 8; i++) {
      const t = i / 7;
      ctx.beginPath(); ctx.moveTo(W * (.1 + t * .8), H * .60); ctx.lineTo(W * t, H); ctx.stroke();
    }
  }
  const win = ctx.createLinearGradient(0, H * .08, 0, H * .32);
  win.addColorStop(0, "#F6C87E"); win.addColorStop(1, "#EFA85E");
  ctx.fillStyle = win; ctx.fillRect(W * .34, H * .08, W * .32, H * .24);
  ctx.strokeStyle = "#8A6A48"; ctx.lineWidth = fs ? 5 : 2; ctx.strokeRect(W * .34, H * .08, W * .32, H * .24);
  ctx.lineWidth = fs ? 3 : 1;
  ctx.beginPath(); ctx.moveTo(W * .50, H * .08); ctx.lineTo(W * .50, H * .32); ctx.moveTo(W * .34, H * .20); ctx.lineTo(W * .66, H * .20); ctx.stroke();
  ctx.fillStyle = "rgba(216,98,81,.78)"; ctx.fillRect(W * .30, H * .07, W * .04, H * .27); ctx.fillRect(W * .66, H * .07, W * .04, H * .27);
  if (fs) {
    ctx.fillStyle = "rgba(150,100,60,.45)";
    ctx.beginPath(); ctx.moveTo(W * .34, H * .27); ctx.quadraticCurveTo(W * .44, H * .22, W * .52, H * .27); ctx.quadraticCurveTo(W * .59, H * .23, W * .66, H * .27); ctx.lineTo(W * .66, H * .32); ctx.lineTo(W * .34, H * .32); ctx.closePath(); ctx.fill();
  }
  ctx.fillStyle = "#8A5A34"; ctx.fillRect(0, H * .41, W * .28, H * .18);
  ctx.fillStyle = "#A06C3F"; ctx.fillRect(0, H * .38, W * .28, H * .04);
  if (fs) {
    ctx.fillStyle = "#6B3E24"; ctx.fillRect(W * .03, H * .45, W * .09, H * .11); ctx.fillRect(W * .15, H * .45, W * .09, H * .11);
    ctx.strokeStyle = "#4A2818"; ctx.lineWidth = 2; ctx.strokeRect(W * .03, H * .45, W * .09, H * .11); ctx.strokeRect(W * .15, H * .45, W * .09, H * .11);
    ctx.fillStyle = "#C85E43"; ctx.beginPath(); ctx.arc(W * .09, H * .36, 10, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#E6C675"; ctx.beginPath(); ctx.arc(W * .18, H * .36, 10, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#5A4A42"; ctx.fillRect(W * .02, H * .335, W * .05, H * .045);
    ctx.strokeStyle = "rgba(255,255,255,.4)"; ctx.lineWidth = 2; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(W * .045, H * .32); ctx.quadraticCurveTo(W * .052, H * .295, W * .043, H * .275); ctx.stroke();
    ctx.strokeStyle = "#5A3A22"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(W * .02, H * .16); ctx.lineTo(W * .26, H * .16); ctx.stroke();
    [[.06, "#8A6A48"], [.13, "#6A4A30"], [.20, "#8A6A48"]].forEach(([x, c]) => {
      ctx.strokeStyle = c; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(W * x, H * .16); ctx.lineTo(W * x, H * .235); ctx.stroke();
      ctx.beginPath(); ctx.ellipse(W * x, H * .255, 7, 9, 0, 0, Math.PI * 2); ctx.stroke();
    });
  }
  ctx.fillStyle = "#8A5A34"; ctx.fillRect(W * .72, H * .41, W * .28, H * .18);
  ctx.fillStyle = "#A06C3F"; ctx.fillRect(W * .72, H * .38, W * .28, H * .04);
  if (fs) {
    ctx.fillStyle = "#6B3E24"; ctx.fillRect(W * .75, H * .45, W * .09, H * .11); ctx.fillRect(W * .87, H * .45, W * .09, H * .11);
    ctx.strokeStyle = "#4A2818"; ctx.lineWidth = 2; ctx.strokeRect(W * .75, H * .45, W * .09, H * .11); ctx.strokeRect(W * .87, H * .45, W * .09, H * .11);
    ctx.fillStyle = "#C7D2D8"; ctx.beginPath(); ctx.ellipse(W * .84, H * .46, 26, 10, 0, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "#7E8A90"; ctx.lineWidth = 2; ctx.stroke();
    ctx.beginPath(); ctx.moveTo(W * .84, H * .40); ctx.quadraticCurveTo(W * .86, H * .37, W * .88, H * .41); ctx.stroke();
  }
  ctx.fillStyle = "#9A6738"; ctx.beginPath(); ctx.ellipse(W * .50, H * .73, W * .20, H * .065, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#70411F"; ctx.fillRect(W * .46, H * .73, W * .03, H * .16); ctx.fillRect(W * .51, H * .73, W * .03, H * .16);
  if (fs) {
    ctx.fillStyle = "#A66C48"; ctx.fillRect(W * .35, H * .74, W * .05, H * .09); ctx.fillRect(W * .60, H * .74, W * .05, H * .09);
    ctx.fillStyle = "#F0D080"; ctx.beginPath(); ctx.arc(W * .50, H * .69, 16, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#C8513A"; ctx.beginPath(); ctx.arc(W * .47, H * .68, 5, 0, Math.PI * 2); ctx.arc(W * .53, H * .67, 5, 0, Math.PI * 2); ctx.fill();
  }
  ctx.fillStyle = "rgba(188,82,68,.55)";
  drawRoundRect(ctx, W * .37, H * .80, W * .26, H * .10, 10); ctx.fill();
}

function drawAtelier(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const wall = ctx.createLinearGradient(0, 0, 0, H * .62);
  wall.addColorStop(0, "#E8CBA2");
  wall.addColorStop(1, "#D9B586");
  ctx.fillStyle = wall; ctx.fillRect(0, 0, W, H);
  const floor = ctx.createLinearGradient(0, H * .62, 0, H);
  floor.addColorStop(0, "#A87848");
  floor.addColorStop(1, "#7E5430");
  ctx.fillStyle = floor; ctx.fillRect(0, H * .62, W, H * .38);
  if (fs) {
    ctx.strokeStyle = "rgba(90,55,25,.28)"; ctx.lineWidth = 2;
    for (let y = H * .68; y < H; y += H * .07) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
  }
  const win = ctx.createLinearGradient(0, H * .10, 0, H * .34);
  win.addColorStop(0, "#F8E3B0"); win.addColorStop(1, "#F0C880");
  ctx.fillStyle = win; ctx.fillRect(W * .74, H * .10, W * .18, H * .24);
  ctx.strokeStyle = "#7A5230"; ctx.lineWidth = fs ? 5 : 2; ctx.strokeRect(W * .74, H * .10, W * .18, H * .24);
  ctx.lineWidth = fs ? 3 : 1;
  ctx.beginPath(); ctx.moveTo(W * .83, H * .10); ctx.lineTo(W * .83, H * .34); ctx.stroke();
  const lx = W * .06, lw = W * .26, ly = H * .14, lh = H * .50;
  ctx.fillStyle = "#6A4222";
  ctx.fillRect(lx, ly, W * .022, lh);
  ctx.fillRect(lx + lw, ly, W * .022, lh);
  ctx.fillRect(lx - W * .01, ly, lw + W * .044, H * .030);
  ctx.fillRect(lx - W * .01, ly + lh * .92, lw + W * .044, H * .028);
  if (fs) {
    ctx.strokeStyle = "rgba(240,225,195,.85)"; ctx.lineWidth = 1.4;
    for (let i = 0; i <= 12; i++) {
      const x = lx + W * .03 + (lw - W * .04) * i / 12;
      ctx.beginPath(); ctx.moveTo(x, ly + H * .03); ctx.lineTo(x, ly + lh * .92); ctx.stroke();
    }
    const bands = [["#8B3A28", .46], ["#C99A3F", .55], ["#4A5D43", .61], ["#B8542E", .67], ["#6A4A78", .74]];
    bands.forEach(([c, t]) => {
      ctx.fillStyle = c;
      ctx.fillRect(lx + W * .022, ly + lh * t, lw - W * .02, lh * .065);
    });
    ctx.fillStyle = "rgba(255,240,210,.35)";
    for (let i = 0; i < 6; i++) ctx.fillRect(lx + W * .04 + i * (lw - W * .06) / 6, ly + lh * .58, 4, 4);
    ctx.fillStyle = "#4A2A14";
    ctx.beginPath(); ctx.ellipse(lx + lw * .55, ly + lh * .42, W * .035, H * .012, .1, 0, Math.PI * 2); ctx.fill();
  }
  const rugs = [
    [.40, "#A84832", "#E8C878", ["#4A5D43", "#C99A3F"]],
    [.55, "#4A5D43", "#E8D8B8", ["#8B3A28", "#D8681C"]],
    [.68, "#C99A3F", "#8B3A28", ["#6A4A78", "#E8D8B8"]],
  ];
  ctx.strokeStyle = "#5A3A22"; ctx.lineWidth = fs ? 4 : 1.5;
  ctx.beginPath(); ctx.moveTo(W * .37, H * .10); ctx.lineTo(W * .72, H * .10); ctx.stroke();
  rugs.forEach(([x, base, edge, stripes]) => {
    const rx = W * x, rw = W * .10, ry = H * .105, rh = H * .26;
    ctx.fillStyle = base; ctx.fillRect(rx, ry, rw, rh);
    ctx.fillStyle = edge;
    ctx.fillRect(rx, ry, rw, rh * .08); ctx.fillRect(rx, ry + rh * .92, rw, rh * .08);
    if (fs) {
      stripes.forEach((c, i) => {
        ctx.fillStyle = c; ctx.fillRect(rx, ry + rh * (.28 + i * .24), rw, rh * .10);
      });
      ctx.fillStyle = edge;
      ctx.save(); ctx.translate(rx + rw / 2, ry + rh * .55); ctx.rotate(Math.PI / 4); ctx.fillRect(-4, -4, 8, 8); ctx.restore();
      ctx.strokeStyle = base; ctx.lineWidth = 1.5;
      for (let i = 0; i < 6; i++) {
        ctx.beginPath(); ctx.moveTo(rx + rw * (i + .5) / 6, ry + rh); ctx.lineTo(rx + rw * (i + .5) / 6, ry + rh + 6); ctx.stroke();
      }
    }
  });
  if (fs) {
    ctx.fillStyle = "#8A5A30";
    ctx.beginPath(); ctx.moveTo(W * .55, H * .76); ctx.lineTo(W * .65, H * .76); ctx.lineTo(W * .635, H * .86); ctx.lineTo(W * .565, H * .86); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = "rgba(60,30,10,.35)"; ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.moveTo(W * .553, H * .795); ctx.lineTo(W * .647, H * .795); ctx.moveTo(W * .558, H * .825); ctx.lineTo(W * .642, H * .825); ctx.stroke();
    [["#C84838", .575, .745], ["#C99A3F", .60, .735], ["#4A5D43", .625, .748]].forEach(([c, x, y]) => {
      ctx.fillStyle = c; ctx.beginPath(); ctx.arc(W * x, H * y, 8, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = "rgba(255,255,255,.30)"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.arc(W * x, H * y, 5, .5, 2.8); ctx.stroke();
    });
    ctx.fillStyle = "#6A4222";
    ctx.beginPath(); ctx.ellipse(W * .44, H * .78, W * .045, H * .016, 0, 0, Math.PI * 2); ctx.fill();
    ctx.fillRect(W * .415, H * .78, W * .012, H * .075); ctx.fillRect(W * .455, H * .78, W * .012, H * .075);
  }
}

function drawMarket(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H * .58);
  sky.addColorStop(0, "#F6D28A");
  sky.addColorStop(.65, "#F7E0A8");
  sky.addColorStop(1, "#E9C985");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  if (fs) {
    const buildings = [
      [.04, .26, .14, .26, "#C99668"], [.19, .23, .16, .30, "#B9855E"], [.67, .25, .13, .28, "#C79361"], [.82, .22, .14, .31, "#AA7A58"],
    ];
    buildings.forEach(([x, y, w, h, c]) => {
      ctx.fillStyle = c; ctx.fillRect(W * x, H * y, W * w, H * h);
      ctx.fillStyle = "rgba(255,235,180,.55)";
      for (let yy = H * (y + .05); yy < H * (y + h - .03); yy += H * .075) {
        for (let xx = W * (x + .025); xx < W * (x + w - .02); xx += W * .055) ctx.fillRect(xx, yy, W * .028, H * .038);
      }
    });
  }
  ctx.fillStyle = "#C49A5A"; ctx.fillRect(0, H * .55, W, H * .45);
  const ground = ctx.createLinearGradient(0, H * .55, 0, H);
  ground.addColorStop(0, "rgba(255,235,165,.22)");
  ground.addColorStop(1, "rgba(110,70,30,.18)");
  ctx.fillStyle = ground; ctx.fillRect(0, H * .55, W, H * .45);
  if (fs) {
    ctx.strokeStyle = "rgba(120,85,45,.20)"; ctx.lineWidth = 1;
    for (let y = H * .60; y < H; y += H * .06) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y + Math.sin(y * .04) * 3); ctx.stroke();
    }
  }
  const stalls = [
    [.18, "#B84232", "#F7C14E"],
    [.50, "#3F7A55", "#F4E3A0"],
    [.82, "#8B5C3E", "#D96C5B"],
  ];
  stalls.forEach(([cx, body, awning], i) => {
    const sw = W * .22, sx = W * cx - sw / 2, sy = H * .30, sh = H * .26;
    ctx.strokeStyle = "#5A351E"; ctx.lineWidth = fs ? 4 : 1.5; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(sx + sw * .08, sy + H * .06); ctx.lineTo(sx + sw * .08, H * .62); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(sx + sw * .92, sy + H * .06); ctx.lineTo(sx + sw * .92, H * .62); ctx.stroke();
    ctx.fillStyle = body; ctx.fillRect(sx, sy + H * .08, sw, sh);
    ctx.fillStyle = "rgba(0,0,0,.12)"; ctx.fillRect(sx, sy + H * .08, sw, sh * .18);
    ctx.fillStyle = awning;
    ctx.beginPath(); ctx.moveTo(sx - sw * .08, sy + H * .08); ctx.lineTo(sx + sw * 1.08, sy + H * .08); ctx.lineTo(sx + sw * .96, sy + H * .16); ctx.lineTo(sx + sw * .04, sy + H * .16); ctx.closePath(); ctx.fill();
    ctx.fillStyle = "rgba(255,255,255,.28)";
    for (let k = 0; k < 4; k++) ctx.fillRect(sx + sw * (k / 4), sy + H * .085, sw * .12, H * .065);
    ctx.fillStyle = "#6A3C22"; ctx.fillRect(sx + sw * .08, H * .50, sw * .84, H * .045);
    if (fs) {
      const goods = i === 0 ? ["#D43A2F", "#F2B635", "#5CA24A"] : i === 1 ? ["#E6B45E", "#C68A3E", "#F0D28A"] : ["#C99A3F", "#8B5C88", "#E8895A"];
      goods.forEach((g, gi) => {
        ctx.fillStyle = g;
        for (let n = 0; n < 3; n++) {
          ctx.beginPath(); ctx.arc(sx + sw * .20 + gi * sw * .22 + n * sw * .035, H * .485 - (n % 2) * 4, fs ? 7 : 2.2, 0, Math.PI * 2); ctx.fill();
        }
      });
    }
  });
  if (fs) {
    ctx.strokeStyle = "rgba(75,45,25,.55)"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(W * .08, H * .20); ctx.quadraticCurveTo(W * .5, H * .27, W * .92, H * .20); ctx.stroke();
    for (let i = 0; i < 9; i++) {
      const x = W * (.1 + i * .1), y = H * (.205 + .035 * Math.sin(i / 8 * Math.PI));
      ctx.fillStyle = "rgba(255,226,135,.88)";
      ctx.beginPath(); ctx.arc(x, y, 5, 0, Math.PI * 2); ctx.fill();
    }
  }
}

function drawGarden(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H * .55);
  sky.addColorStop(0, "#F8E2AC");
  sky.addColorStop(.6, "#FCF0D0");
  sky.addColorStop(1, "#F0E8C2");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "rgba(255,225,150,.7)";
  ctx.beginPath(); ctx.arc(W * .15, H * .15, fs ? 26 : 8, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "rgba(255,225,150,.18)";
  ctx.beginPath(); ctx.arc(W * .15, H * .15, fs ? 52 : 15, 0, Math.PI * 2); ctx.fill();
  if (fs) {
    ctx.fillStyle = "rgba(255,252,240,.65)";
    [[.42, .12, .09, .035], [.62, .10, .12, .04], [.82, .22, .08, .03]].forEach(([x, y, rx, ry]) => {
      ctx.beginPath(); ctx.ellipse(W * x, H * y, W * rx, H * ry, 0, 0, Math.PI * 2); ctx.fill();
    });
  }
  ctx.fillStyle = "#6F9C47"; ctx.fillRect(0, H * .48, W, H * .52);
  ctx.fillStyle = "#86B95C"; ctx.fillRect(0, H * .48, W, H * .06);
  ctx.fillStyle = "#D7B37A";
  ctx.beginPath();
  ctx.moveTo(W * .42, H * .52); ctx.bezierCurveTo(W * .36, H * .66, W * .34, H * .84, W * .28, H);
  ctx.lineTo(W * .72, H); ctx.bezierCurveTo(W * .65, H * .84, W * .62, H * .66, W * .58, H * .52);
  ctx.closePath(); ctx.fill();
  const beds = [
    [.06, .58, .24, .18, "#5A351F", "#E85A4A", "#F3D34A"],
    [.38, .56, .24, .20, "#4D2C18", "#B86AA8", "#F4D0A0"],
    [.70, .58, .24, .18, "#5A351F", "#F19A38", "#F6D860"],
  ];
  beds.forEach(([x, y, w, h, soil, flower, center]) => {
    ctx.fillStyle = soil; ctx.fillRect(W * x, H * y, W * w, H * h);
    ctx.strokeStyle = "rgba(50,25,10,.35)"; ctx.lineWidth = fs ? 3 : 1; ctx.strokeRect(W * x, H * y, W * w, H * h);
    if (fs) {
      for (let i = 0; i < 5; i++) {
        const fx = W * (x + .04 + i * w / 5.6), fy = H * (y + .11 + (i % 2) * .035);
        ctx.strokeStyle = "#2F6D2E"; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.moveTo(fx, fy + 12); ctx.lineTo(fx, fy - 4); ctx.stroke();
        for (let a = 0; a < Math.PI * 2; a += Math.PI / 3) {
          ctx.fillStyle = flower; ctx.beginPath(); ctx.ellipse(fx + Math.cos(a) * 7, fy + Math.sin(a) * 5, 5, 3, a, 0, Math.PI * 2); ctx.fill();
        }
        ctx.fillStyle = center; ctx.beginPath(); ctx.arc(fx, fy, 3, 0, Math.PI * 2); ctx.fill();
      }
    }
  });
  if (fs) {
    ctx.strokeStyle = "#B98A55"; ctx.lineWidth = 5; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(0, H * .50); ctx.lineTo(W, H * .50); ctx.stroke();
    for (let x = 0; x < W; x += W * .08) {
      ctx.beginPath(); ctx.moveTo(x, H * .45); ctx.lineTo(x, H * .56); ctx.stroke();
    }
    [["#E8A040", .34, .36], ["#C86A88", .66, .30]].forEach(([c, x, y]) => {
      ctx.fillStyle = c;
      ctx.beginPath(); ctx.ellipse(W * x - 4, H * y, 4.5, 3, -.5, 0, Math.PI * 2); ctx.fill();
      ctx.beginPath(); ctx.ellipse(W * x + 4, H * y, 4.5, 3, .5, 0, Math.PI * 2); ctx.fill();
    });
  }
}

function drawField(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H * .5);
  sky.addColorStop(0, "#EFB963");
  sky.addColorStop(.55, "#F7D794");
  sky.addColorStop(1, "#FBE9BC");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "rgba(255,235,160,.85)";
  ctx.beginPath(); ctx.arc(W * .30, H * .18, fs ? 34 : 10, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "rgba(255,235,160,.20)";
  ctx.beginPath(); ctx.arc(W * .30, H * .18, fs ? 66 : 19, 0, Math.PI * 2); ctx.fill();
  if (fs) {
    ctx.strokeStyle = "rgba(80,45,20,.7)"; ctx.lineWidth = 1.8; ctx.lineCap = "round";
    [[.58, .14], [.64, .17], [.70, .13]].forEach(([x, y]) => {
      ctx.beginPath(); ctx.moveTo(W * x - 7, H * y); ctx.quadraticCurveTo(W * x - 1, H * y - 5, W * x, H * y); ctx.quadraticCurveTo(W * x + 1, H * y - 5, W * x + 7, H * y); ctx.stroke();
    });
  }
  ctx.fillStyle = "#B98E4E";
  ctx.beginPath(); ctx.moveTo(0, H * .46); ctx.bezierCurveTo(W * .25, H * .40, W * .55, H * .49, W, H * .42); ctx.lineTo(W, H * .52); ctx.lineTo(0, H * .52); ctx.closePath(); ctx.fill();
  const tx = W * .84, ty = H * .30;
  ctx.fillStyle = "#5A3A1C"; ctx.fillRect(tx - W * .008, ty + H * .05, W * .016, H * .11);
  ["#5C7A38", "#6E8E42"].forEach((c, i) => {
    ctx.fillStyle = c;
    ctx.beginPath(); ctx.ellipse(tx, ty + H * (.02 - i * .02), W * (.055 - i * .012), H * (.075 - i * .015), 0, 0, Math.PI * 2); ctx.fill();
  });
  const field = ctx.createLinearGradient(0, H * .50, 0, H);
  field.addColorStop(0, "#E2B45E");
  field.addColorStop(.5, "#D29E48");
  field.addColorStop(1, "#B37F34");
  ctx.fillStyle = field; ctx.fillRect(0, H * .50, W, H * .50);
  if (fs) {
    ctx.strokeStyle = "rgba(120,80,30,.30)"; ctx.lineWidth = 2;
    for (let i = 0; i < 9; i++) {
      const t = i / 8;
      ctx.beginPath(); ctx.moveTo(W * (.2 + t * .6), H * .52); ctx.lineTo(W * (t * 1.25 - .12), H); ctx.stroke();
    }
    ctx.strokeStyle = "rgba(250,220,140,.85)"; ctx.lineWidth = 2; ctx.lineCap = "round";
    for (let i = 0; i < 26; i++) {
      const x = seeded01(70, i) * W, y = H * (.56 + seeded01(71, i) * .10), h = 8 + seeded01(72, i) * 8;
      ctx.beginPath(); ctx.moveTo(x, y); ctx.quadraticCurveTo(x + 3, y - h * .6, x + 1, y - h); ctx.stroke();
      ctx.fillStyle = "rgba(240,200,110,.9)";
      ctx.beginPath(); ctx.ellipse(x + 1, y - h, 2.4, 4.5, .3, 0, Math.PI * 2); ctx.fill();
    }
  }
  [[.16, .66, .09], [.70, .62, .065]].forEach(([x, y, s]) => {
    ctx.fillStyle = "#D8A850";
    ctx.beginPath(); ctx.moveTo(W * (x - s), H * (y + .10));
    ctx.quadraticCurveTo(W * x, H * (y - .16), W * (x + s), H * (y + .10));
    ctx.closePath(); ctx.fill();
    ctx.fillStyle = "#EFC470";
    ctx.beginPath(); ctx.moveTo(W * (x - s * .55), H * (y + .10));
    ctx.quadraticCurveTo(W * (x - s * .1), H * (y - .12), W * (x + s * .3), H * (y + .10));
    ctx.closePath(); ctx.fill();
    if (fs) {
      ctx.strokeStyle = "rgba(140,95,35,.5)"; ctx.lineWidth = 1.6;
      ctx.beginPath(); ctx.moveTo(W * (x - s * .5), H * (y + .02)); ctx.quadraticCurveTo(W * x, H * (y - .02), W * (x + s * .5), H * (y + .02)); ctx.stroke();
    }
  });
  if (fs) {
    ctx.strokeStyle = "#7A5228"; ctx.lineWidth = 5; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(0, H * .83); ctx.lineTo(W * .24, H * .86); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, H * .91); ctx.lineTo(W * .24, H * .94); ctx.stroke();
    [[.03], [.12], [.21]].forEach(([x]) => {
      ctx.beginPath(); ctx.moveTo(W * x, H * .79); ctx.lineTo(W * x, H * .98); ctx.stroke();
    });
  }
}

function drawRiverside(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H * .55);
  sky.addColorStop(0, "#F7B95F");
  sky.addColorStop(.62, "#F8D894");
  sky.addColorStop(1, "#E7B869");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "rgba(255,220,120,.72)";
  ctx.beginPath(); ctx.arc(W * .76, H * .20, fs ? 48 : 14, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "rgba(255,220,120,.18)";
  ctx.beginPath(); ctx.arc(W * .76, H * .20, fs ? 88 : 25, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#6E8F48";
  ctx.beginPath();
  ctx.moveTo(0, H * .46);
  ctx.bezierCurveTo(W * .25, H * .39, W * .45, H * .51, W * .66, H * .43);
  ctx.bezierCurveTo(W * .82, H * .37, W * .92, H * .44, W, H * .40);
  ctx.lineTo(W, H * .54); ctx.lineTo(0, H * .54); ctx.closePath(); ctx.fill();
  const water = ctx.createLinearGradient(0, H * .50, 0, H * .80);
  water.addColorStop(0, "#C9A868");
  water.addColorStop(.4, "#7E9880");
  water.addColorStop(1, "#4A6E66");
  ctx.fillStyle = water; ctx.fillRect(0, H * .50, W, H * .30);
  ctx.fillStyle = "rgba(255,215,120,.35)";
  ctx.beginPath(); ctx.ellipse(W * .76, H * .58, W * .06, H * .014, 0, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.ellipse(W * .75, H * .64, W * .045, H * .011, 0, 0, Math.PI * 2); ctx.fill();
  if (fs) {
    ctx.strokeStyle = "rgba(255,245,215,.35)"; ctx.lineWidth = 2;
    for (let y = H * .55; y < H * .76; y += H * .045) {
      ctx.beginPath(); ctx.moveTo(0, y);
      for (let x = 0; x <= W; x += W * .14) ctx.quadraticCurveTo(x + W * .07, y - 5, x + W * .14, y);
      ctx.stroke();
    }
  }
  ctx.fillStyle = "#577A38"; ctx.fillRect(0, H * .78, W, H * .22);
  ctx.fillStyle = "#6D9446";
  ctx.beginPath(); ctx.moveTo(0, H * .78); ctx.bezierCurveTo(W * .25, H * .72, W * .55, H * .82, W, H * .76); ctx.lineTo(W, H); ctx.lineTo(0, H); ctx.closePath(); ctx.fill();
  if (fs) {
    ctx.fillStyle = "#8B5A30"; ctx.fillRect(W * .14, H * .68, W * .20, H * .04);
    ctx.fillRect(W * .17, H * .70, W * .025, H * .12);
    ctx.fillRect(W * .29, H * .70, W * .025, H * .12);
    ctx.strokeStyle = "rgba(70,35,15,.45)"; ctx.lineWidth = 2;
    for (let i = 0; i < 4; i++) {
      ctx.beginPath(); ctx.moveTo(W * (.15 + i * .05), H * .68); ctx.lineTo(W * (.16 + i * .05), H * .72); ctx.stroke();
    }
    ctx.fillStyle = "#7A4424";
    ctx.beginPath(); ctx.moveTo(W * .52, H * .665); ctx.quadraticCurveTo(W * .57, H * .705, W * .62, H * .665); ctx.lineTo(W * .605, H * .66); ctx.quadraticCurveTo(W * .57, H * .688, W * .535, H * .66); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = "#4A6A2A"; ctx.lineWidth = 2.2; ctx.lineCap = "round";
    [[.86, .82], [.895, .80], [.925, .83]].forEach(([x, y]) => {
      ctx.beginPath(); ctx.moveTo(W * x, H * (y + .08)); ctx.lineTo(W * x, H * (y - .04)); ctx.stroke();
      ctx.fillStyle = "#6A4222";
      ctx.beginPath(); ctx.ellipse(W * x, H * (y - .055), 3.4, 8, 0, 0, Math.PI * 2); ctx.fill();
    });
  }
}

function drawPlaza(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H * .55);
  sky.addColorStop(0, "#C39BB4");
  sky.addColorStop(.6, "#F1C9C4");
  sky.addColorStop(1, "#F5DDB4");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  const buildings = [
    [.04, .20, .17, .36, "#B99983"], [.24, .17, .15, .39, "#A98C78"], [.62, .18, .15, .38, "#B08F78"], [.80, .21, .16, .35, "#9E8275"],
  ];
  buildings.forEach(([x, y, w, h, c]) => {
    ctx.fillStyle = c; ctx.fillRect(W * x, H * y, W * w, H * h);
    if (fs) {
      ctx.fillStyle = "rgba(255,215,140,.55)";
      for (let yy = H * (y + .06); yy < H * (y + h - .05); yy += H * .075) {
        for (let xx = W * (x + .025); xx < W * (x + w - .02); xx += W * .055) ctx.fillRect(xx, yy, W * .03, H * .045);
      }
      ctx.fillStyle = "#7A5240";
      ctx.beginPath(); ctx.moveTo(W * (x - .012), H * y); ctx.lineTo(W * (x + w / 2), H * (y - .05)); ctx.lineTo(W * (x + w + .012), H * y); ctx.closePath(); ctx.fill();
    }
  });
  ctx.fillStyle = "#D2C0A6"; ctx.fillRect(0, H * .56, W, H * .44);
  if (fs) {
    ctx.strokeStyle = "rgba(145,120,90,.32)"; ctx.lineWidth = 1;
    for (let y = H * .59; y < H; y += H * .055) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
    for (let x = 0; x < W; x += W * .07) {
      ctx.beginPath(); ctx.moveTo(x, H * .56); ctx.lineTo(x - W * .08, H); ctx.stroke();
    }
  }
  const cx = W * .5, cy = H * .56;
  ctx.fillStyle = "#8E9A88"; ctx.beginPath(); ctx.ellipse(cx, cy + H * .10, W * .19, H * .055, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#B4C4AE"; ctx.beginPath(); ctx.ellipse(cx, cy + H * .08, W * .14, H * .04, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#7E8A78"; ctx.fillRect(cx - W * .04, cy - H * .02, W * .08, H * .12);
  ctx.fillStyle = "#C8D8C4"; ctx.beginPath(); ctx.arc(cx, cy - H * .03, fs ? 22 : 7, 0, Math.PI * 2); ctx.fill();
  if (fs) {
    ctx.strokeStyle = "rgba(235,250,235,.75)"; ctx.lineWidth = 2;
    [-.04, 0, .04].forEach((dx) => {
      ctx.beginPath(); ctx.moveTo(cx, cy - H * .04); ctx.quadraticCurveTo(cx + W * dx, cy - H * .12, cx + W * dx, cy + H * .02); ctx.stroke();
    });
    [[.12], [.88]].forEach(([x]) => {
      ctx.strokeStyle = "#3A2C22"; ctx.lineWidth = 4; ctx.lineCap = "round";
      ctx.beginPath(); ctx.moveTo(W * x, H * .62); ctx.lineTo(W * x, H * .40); ctx.stroke();
      const lg = ctx.createRadialGradient(W * x, H * .385, 0, W * x, H * .385, 44);
      lg.addColorStop(0, "rgba(255,210,120,.35)"); lg.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = lg; ctx.fillRect(W * x - 46, H * .385 - 46, 92, 92);
      ctx.fillStyle = "#F4C86A";
      ctx.beginPath(); ctx.arc(W * x, H * .385, 7, 0, Math.PI * 2); ctx.fill();
    });
  }
}

function drawForest(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H);
  sky.addColorStop(0, "#12261F");
  sky.addColorStop(.55, "#244C36");
  sky.addColorStop(1, "#496D47");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  if (fs) {
    const glow = ctx.createRadialGradient(W * .52, H * .32, 10, W * .52, H * .32, W * .5);
    glow.addColorStop(0, "rgba(230,225,150,.16)");
    glow.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = "rgba(240,230,170,.07)";
    [[.40, .14], [.55, .10], [.48, .18]].forEach(([x, w]) => {
      ctx.beginPath();
      ctx.moveTo(W * (x - .015), 0); ctx.lineTo(W * (x + .015), 0);
      ctx.lineTo(W * (x + w), H * .85); ctx.lineTo(W * (x + w - .08), H * .85);
      ctx.closePath(); ctx.fill();
    });
  }
  const trees = [
    [.07, .18, .36], [.16, .11, .45], [.29, .16, .40], [.72, .12, .45], [.86, .14, .40], [.95, .20, .34],
    [.42, .08, .48], [.58, .10, .46],
  ];
  trees.forEach(([x, y, s]) => {
    const tx = W * x, top = H * y, trunkH = H * (.55 * s + .18);
    ctx.fillStyle = "#3A2313"; ctx.fillRect(tx - W * .012, top + H * .18, W * .024, trunkH);
    ["#17381E", "#214A25", "#2B5A2E"].forEach((c, j) => {
      ctx.fillStyle = c;
      ctx.beginPath(); ctx.ellipse(tx, top + H * (.12 + j * .075), W * (.07 - j * .012), H * (.12 - j * .018), 0, 0, Math.PI * 2); ctx.fill();
    });
  });
  ctx.fillStyle = "#416C3D";
  ctx.beginPath(); ctx.ellipse(W * .5, H * .78, W * .38, H * .17, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#567C44";
  ctx.beginPath(); ctx.ellipse(W * .5, H * .82, W * .28, H * .10, 0, 0, Math.PI * 2); ctx.fill();
  if (fs) {
    [[.28, .78, "#C85448"], [.70, .80, "#E7C45E"], [.61, .73, "#D8D0B8"]].forEach(([x, y, c]) => {
      ctx.fillStyle = c; ctx.beginPath(); ctx.ellipse(W * x, H * y, 12, 7, 0, Math.PI, Math.PI * 2); ctx.fill();
      ctx.fillStyle = "#EEE0C8"; ctx.fillRect(W * x - 3, H * y, 6, 12);
    });
    for (let i = 0; i < 10; i++) {
      const x = W * (.25 + seeded01(80, i) * .5), y = H * (.30 + seeded01(81, i) * .35);
      ctx.fillStyle = `rgba(240,235,160,${.35 + seeded01(82, i) * .45})`;
      ctx.beginPath(); ctx.arc(x, y, 1.5 + seeded01(83, i) * 1.6, 0, Math.PI * 2); ctx.fill();
    }
  }
}

function drawClassroom(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H * .58);
  sky.addColorStop(0, "#F5DFA8");
  sky.addColorStop(.7, "#FBF0D2");
  sky.addColorStop(1, "#F0EBCC");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  if (fs) {
    ctx.fillStyle = "rgba(255,252,242,.68)";
    [[.16, .13, .09, .035], [.68, .11, .12, .038], [.84, .24, .08, .03]].forEach(([x, y, rx, ry]) => {
      ctx.beginPath(); ctx.ellipse(W * x, H * y, W * rx, H * ry, 0, 0, Math.PI * 2); ctx.fill();
    });
  }
  ctx.fillStyle = "#95BE63";
  ctx.beginPath(); ctx.moveTo(0, H * .52); ctx.bezierCurveTo(W * .18, H * .47, W * .35, H * .56, W * .52, H * .50); ctx.bezierCurveTo(W * .72, H * .43, W * .86, H * .55, W, H * .48); ctx.lineTo(W, H * .62); ctx.lineTo(0, H * .62); ctx.closePath(); ctx.fill();
  ctx.fillStyle = "#78A84F"; ctx.fillRect(0, H * .58, W, H * .42);
  ctx.fillStyle = "#86B85A"; ctx.fillRect(0, H * .58, W, H * .05);
  const tx = W * .42, ty = H * .34;
  ctx.fillStyle = "#6A411D";
  ctx.fillRect(tx - W * .022, ty, W * .044, H * .34);
  if (fs) {
    ctx.fillStyle = "rgba(0,0,0,.10)"; ctx.fillRect(tx - W * .006, ty + H * .02, W * .012, H * .30);
  }
  ["#2D6A2F", "#3E7E37", "#4E9241"].forEach((c, i) => {
    ctx.fillStyle = c;
    if (i === 0) { ctx.beginPath(); ctx.ellipse(tx, ty - H * .06, W * .23, H * .15, 0, 0, Math.PI * 2); ctx.fill(); }
    if (i === 1) { ctx.beginPath(); ctx.ellipse(tx - W * .09, ty + H * .00, W * .18, H * .12, -.25, 0, Math.PI * 2); ctx.fill(); }
    if (i === 2) { ctx.beginPath(); ctx.ellipse(tx + W * .09, ty - H * .01, W * .18, H * .12, .25, 0, Math.PI * 2); ctx.fill(); }
  });
  if (fs) {
    ctx.fillStyle = "rgba(45,80,40,.14)";
    ctx.beginPath(); ctx.ellipse(tx + W * .02, H * .72, W * .24, H * .07, 0, 0, Math.PI * 2); ctx.fill();
  }
  ctx.fillStyle = "#324C38"; ctx.fillRect(W * .70, H * .34, W * .18, H * .13);
  ctx.strokeStyle = "#8A6A42"; ctx.lineWidth = fs ? 4 : 1.5; ctx.strokeRect(W * .70, H * .34, W * .18, H * .13);
  if (fs) {
    ctx.strokeStyle = "#E9E5CF"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(W * .73, H * .38); ctx.lineTo(W * .84, H * .38); ctx.moveTo(W * .73, H * .42); ctx.lineTo(W * .81, H * .42); ctx.stroke();
    ctx.fillStyle = "#8A6A42"; ctx.fillRect(W * .725, H * .47, W * .012, H * .11); ctx.fillRect(W * .845, H * .47, W * .012, H * .11);
  }
  ctx.fillStyle = "#CDA76A"; ctx.beginPath(); ctx.ellipse(W * .48, H * .77, W * .26, H * .09, 0, 0, Math.PI * 2); ctx.fill();
  if (fs) {
    const mats = [["#C96B54", .36, .76], ["#C99A3F", .48, .79], ["#E0B24A", .60, .76]];
    mats.forEach(([c, x, y]) => { ctx.fillStyle = c; ctx.beginPath(); ctx.ellipse(W * x, H * y, W * .09, H * .03, 0, 0, Math.PI * 2); ctx.fill(); });
    ctx.fillStyle = "#F4ECD8";
    ctx.beginPath(); ctx.moveTo(W * .455, H * .775); ctx.lineTo(W * .48, H * .785); ctx.lineTo(W * .505, H * .775); ctx.lineTo(W * .505, H * .795); ctx.lineTo(W * .48, H * .805); ctx.lineTo(W * .455, H * .795); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = "rgba(90,60,30,.5)"; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(W * .48, H * .785); ctx.lineTo(W * .48, H * .805); ctx.stroke();
  }
  ctx.strokeStyle = "#75461F"; ctx.lineWidth = fs ? 6 : 2; ctx.lineCap = "round";
  [[.22, .72], [.74, .72]].forEach(([x, y]) => {
    ctx.beginPath(); ctx.moveTo(W * (x - .07), H * y); ctx.lineTo(W * (x + .07), H * y); ctx.stroke();
  });
}

function drawNight(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const sky = ctx.createLinearGradient(0, 0, 0, H);
  sky.addColorStop(0, "#170E20");
  sky.addColorStop(.60, "#33203C");
  sky.addColorStop(1, "#0E0B08");
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  if (fs) {
    for (let i = 0; i < 90; i++) {
      const sx = seeded01(31, i) * W, sy = seeded01(32, i) * H * .52, r = .6 + seeded01(33, i) * 1.4;
      ctx.fillStyle = `rgba(255,246,210,${.35 + seeded01(34, i) * .55})`;
      ctx.beginPath(); ctx.arc(sx, sy, r, 0, Math.PI * 2); ctx.fill();
    }
  }
  ctx.fillStyle = "rgba(240,232,196,.92)"; ctx.beginPath(); ctx.arc(W * .80, H * .15, fs ? 25 : 7, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#170E20"; ctx.beginPath(); ctx.arc(W * .81, H * .135, fs ? 24 : 7, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#1E1A14";
  ctx.beginPath(); ctx.moveTo(0, H * .56); ctx.bezierCurveTo(W * .18, H * .46, W * .34, H * .61, W * .50, H * .52); ctx.bezierCurveTo(W * .72, H * .42, W * .86, H * .57, W, H * .49); ctx.lineTo(W, H); ctx.lineTo(0, H); ctx.closePath(); ctx.fill();
  ctx.fillStyle = "#12100A"; ctx.beginPath(); ctx.moveTo(0, H * .68); ctx.bezierCurveTo(W * .24, H * .61, W * .42, H * .72, W * .60, H * .65); ctx.bezierCurveTo(W * .78, H * .59, W * .90, H * .70, W, H * .64); ctx.lineTo(W, H); ctx.lineTo(0, H); ctx.closePath(); ctx.fill();
  ctx.fillStyle = "#191510"; ctx.beginPath(); ctx.ellipse(W * .5, H * .82, W * .28, H * .10, 0, 0, Math.PI * 2); ctx.fill();
  if (fs) {
    ctx.strokeStyle = "rgba(255,210,120,.65)"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(W * .24, H * .45); ctx.quadraticCurveTo(W * .5, H * .55, W * .76, H * .45); ctx.stroke();
    for (let i = 0; i < 7; i++) {
      const x = W * (.26 + i * .083), y = H * (.46 + .032 * Math.sin(i / 6 * Math.PI));
      ctx.fillStyle = "rgba(255,210,100,.92)"; ctx.beginPath(); ctx.arc(x, y, 5, 0, Math.PI * 2); ctx.fill();
    }
    const glow = ctx.createRadialGradient(W * .5, H * .80, 0, W * .5, H * .80, W * .20);
    glow.addColorStop(0, "rgba(255,140,40,.42)");
    glow.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = glow; ctx.fillRect(W * .28, H * .60, W * .44, H * .34);
    ctx.strokeStyle = "#6A3A18"; ctx.lineWidth = 5; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(W * .46, H * .84); ctx.lineTo(W * .54, H * .80); ctx.moveTo(W * .46, H * .80); ctx.lineTo(W * .54, H * .84); ctx.stroke();
    ctx.fillStyle = "rgba(232,82,25,.95)";
    ctx.beginPath(); ctx.moveTo(W * .5, H * .70); ctx.bezierCurveTo(W * .46, H * .76, W * .46, H * .84, W * .5, H * .83); ctx.bezierCurveTo(W * .54, H * .84, W * .54, H * .76, W * .5, H * .70); ctx.closePath(); ctx.fill();
    ctx.fillStyle = "rgba(255,170,45,.88)";
    ctx.beginPath(); ctx.moveTo(W * .5, H * .73); ctx.bezierCurveTo(W * .475, H * .77, W * .478, H * .82, W * .5, H * .81); ctx.bezierCurveTo(W * .522, H * .82, W * .525, H * .77, W * .5, H * .73); ctx.closePath(); ctx.fill();
    for (let i = 0; i < 8; i++) {
      const x = W * (.48 + seeded01(90, i) * .04), y = H * (.68 - seeded01(91, i) * .14);
      ctx.fillStyle = `rgba(255,190,80,${.3 + seeded01(92, i) * .5})`;
      ctx.beginPath(); ctx.arc(x, y, 1.2 + seeded01(93, i) * 1.4, 0, Math.PI * 2); ctx.fill();
    }
    ctx.fillStyle = "#7C7F80";
    for (let i = 0; i < 10; i++) {
      const a = (Math.PI * 2 * i) / 10, rx = W * .11, ry = H * .04;
      const x = W * .5 + Math.cos(a) * rx, y = H * .82 + Math.sin(a) * ry;
      ctx.beginPath(); ctx.ellipse(x, y, 7, 4, 0, 0, Math.PI * 2); ctx.fill();
    }
  }
}

function drawCelebration(canvas, fs) {
  const ctx = bgCtx(canvas), W = canvas.width, H = canvas.height;
  const bg = ctx.createRadialGradient(W * .5, H * .38, 0, W * .5, H * .38, W * .9);
  bg.addColorStop(0, "#F8D08A");
  bg.addColorStop(.55, "#CF7740");
  bg.addColorStop(1, "#6E2F1C");
  ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "#3A180E"; ctx.fillRect(0, H * .62, W, H * .38);
  if (fs) {
    ctx.strokeStyle = "rgba(45,18,8,.75)"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(0, H * .15);
    for (let x = 0; x <= W; x += W * .10) ctx.quadraticCurveTo(x + W * .05, H * .20, x + W * .10, H * .15);
    ctx.stroke();
    const flags = ["#E84A3A", "#F4C542", "#7A5C88", "#42A85B", "#C85EB8", "#F08A35", "#3E7A70", "#E84A3A", "#F4C542", "#8B3A28"];
    flags.forEach((fc, i) => {
      const x = W * (i * .10 + .045), y = H * (.165 + .035 * Math.sin(i / 9 * Math.PI));
      ctx.fillStyle = fc; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + W * .025, y + H * .045); ctx.lineTo(x - W * .025, y + H * .045); ctx.closePath(); ctx.fill();
    });
  }
  ctx.fillStyle = "#5A2718"; ctx.fillRect(W * .18, H * .70, W * .64, H * .13);
  ctx.fillStyle = "rgba(255,205,110,.18)"; ctx.fillRect(W * .18, H * .70, W * .64, H * .04);
  if (fs) {
    ctx.fillStyle = "#7A4020"; ctx.fillRect(W * .06, H * .575, W * .22, H * .028);
    ctx.fillRect(W * .08, H * .60, W * .018, H * .06); ctx.fillRect(W * .245, H * .60, W * .018, H * .06);
    ctx.fillStyle = "#E8D8B8";
    [[.10], [.155], [.21]].forEach(([x]) => {
      ctx.beginPath(); ctx.ellipse(W * x, H * .572, 9, 3.5, 0, 0, Math.PI * 2); ctx.fill();
    });
    ctx.fillStyle = "#C8513A"; ctx.beginPath(); ctx.arc(W * .155, H * .565, 4, 0, Math.PI * 2); ctx.fill();
    [[.22, .32, "#F4D25B"], [.50, .26, "#F29A4A"], [.78, .32, "#E85A62"]].forEach(([x, y, c]) => {
      ctx.fillStyle = c; ctx.beginPath(); ctx.ellipse(W * x, H * y, 16, 22, 0, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = "rgba(70,25,10,.45)"; ctx.lineWidth = 2; ctx.stroke();
      const glow = ctx.createRadialGradient(W * x, H * y, 0, W * x, H * y, 70);
      glow.addColorStop(0, "rgba(255,220,120,.18)"); glow.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = glow; ctx.fillRect(W * x - 80, H * y - 80, 160, 160);
    });
    const conf = ["#E84A3A", "#F4C542", "#7A5C88", "#42A85B", "#C85EB8", "#F08A35"];
    for (let i = 0; i < 38; i++) {
      ctx.fillStyle = conf[i % conf.length];
      ctx.save(); ctx.translate(seeded01(60, i) * W, seeded01(61, i) * H * .48); ctx.rotate(seeded01(62, i) * Math.PI);
      ctx.fillRect(-3, -1, 6, 2); ctx.restore();
    }
  }
}
