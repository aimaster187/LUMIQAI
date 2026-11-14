document.getElementById('yearNow').textContent = new Date().getFullYear();

const menuToggle = document.getElementById('menuToggle');
const navLinks = document.getElementById('navLinks');
if (menuToggle && navLinks) {
  menuToggle.addEventListener('click', () => navLinks.classList.toggle('open'));
}

// 10D Neural Orb Web Canvas
const canvas = document.getElementById('lumiqOrbCanvas');
if (canvas) {
  const ctx = canvas.getContext('2d');
  let phase = 0;
  let boost = 0;
  const nodes = Array.from({ length: 34 }, (_, i) => ({
    a: (i * 0.6180339) * Math.PI * 2,
    r: 0.25 + (i % 7) * 0.08,
    speed: (i % 2 === 0 ? 1 : -1) * (0.4 + (i % 5) * 0.15)
  }));

  canvas.addEventListener('click', () => { boost = 1.0; });
  canvas.addEventListener('mousemove', () => { boost = Math.max(boost, 0.35); });

  function draw() {
    const w = canvas.width;
    const h = canvas.height;
    const cx = w / 2;
    const cy = h / 2;
    const radius = w * 0.28 + boost * 10;

    ctx.clearRect(0, 0, w, h);

    // Outer ambient glow
    const halo = ctx.createRadialGradient(cx, cy, radius * 0.5, cx, cy, w * 0.48);
    halo.addColorStop(0, 'rgba(0, 212, 255, 0.35)');
    halo.addColorStop(0.55, 'rgba(29, 107, 255, 0.14)');
    halo.addColorStop(1, 'rgba(29, 107, 255, 0)');
    ctx.fillStyle = halo;
    ctx.beginPath();
    ctx.arc(cx, cy, w * 0.48, 0, Math.PI * 2);
    ctx.fill();

    // Precision tick ring
    ctx.save();
    ctx.translate(cx, cy);
    for (let i = 0; i < 72; i++) {
      const ang = (i / 72) * Math.PI * 2 + phase * 0.12;
      const r1 = radius * 1.18;
      const r2 = radius * (i % 3 === 0 ? 1.32 : 1.24);
      ctx.strokeStyle = i % 3 === 0 ? 'rgba(29, 107, 255, 0.55)' : 'rgba(130, 175, 235, 0.35)';
      ctx.lineWidth = i % 3 === 0 ? 2 : 1.2;
      ctx.beginPath();
      ctx.moveTo(Math.cos(ang) * r1, Math.sin(ang) * r1);
      ctx.lineTo(Math.cos(ang) * r2, Math.sin(ang) * r2);
      ctx.stroke();
    }
    ctx.restore();

    // Dark iridescent sphere
    const core = ctx.createRadialGradient(cx - radius * 0.28, cy - radius * 0.28, radius * 0.1, cx, cy, radius);
    core.addColorStop(0, '#1e355d');
    core.addColorStop(0.65, '#0b1629');
    core.addColorStop(1, '#040914');
    ctx.fillStyle = core;
    ctx.beginPath();
    ctx.arc(cx, cy, radius, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = '#00d4ff';
    ctx.lineWidth = 2.5;
    ctx.stroke();

    // Constellation nodes
    const pts = nodes.map((n, idx) => {
      const ang = n.a + phase * n.speed;
      const dist = radius * n.r * (0.85 + 0.15 * Math.sin(phase * 1.7 + idx));
      return { x: cx + Math.cos(ang) * dist, y: cy + Math.sin(ang) * dist };
    });

    ctx.strokeStyle = 'rgba(0, 212, 255, 0.32)';
    ctx.lineWidth = 1;
    for (let i = 0; i < pts.length; i++) {
      const a = pts[i];
      const b = pts[(i + 3) % pts.length];
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
    }

    pts.forEach((p, idx) => {
      ctx.fillStyle = idx % 3 === 0 ? '#00d4ff' : '#80bcff';
      ctx.beginPath();
      ctx.arc(p.x, p.y, idx % 4 === 0 ? 3 : 2, 0, Math.PI * 2);
      ctx.fill();
    });

    phase += 0.014 + boost * 0.03;
    boost *= 0.94;
    requestAnimationFrame(draw);
  }
  draw();
}
