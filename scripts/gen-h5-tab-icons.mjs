/**
 * 生成移动端 tabBar 图标（docs/23 H5-H1-03）：纯 Node（zlib）绘制 81x81 PNG，无第三方依赖。
 * 输出：agentoa-uniapp/src/static/tab/{workbench,approval,attendance,mine}(-active).png
 * 重新生成：node scripts/gen-h5-tab-icons.mjs
 */
import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const SIZE = 81;
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'agentoa-uniapp', 'src', 'static', 'tab');

function crc32(buf) {
  let c;
  const table = [];
  for (let n = 0; n < 256; n++) {
    c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function encodePng(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

function canvas() {
  const px = Buffer.alloc(SIZE * SIZE * 4);
  const set = (x, y, color) => {
    x = Math.round(x);
    y = Math.round(y);
    if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
    const i = (y * SIZE + x) * 4;
    px[i] = color[0];
    px[i + 1] = color[1];
    px[i + 2] = color[2];
    px[i + 3] = color[3];
  };
  const rect = (x0, y0, x1, y1, color) => {
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, color);
  };
  const circle = (cx, cy, r, color) => {
    for (let y = Math.floor(cy - r); y <= cy + r; y++) {
      for (let x = Math.floor(cx - r); x <= cx + r; x++) {
        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) set(x, y, color);
      }
    }
  };
  const ring = (cx, cy, r, w, color) => {
    for (let y = Math.floor(cy - r); y <= cy + r; y++) {
      for (let x = Math.floor(cx - r); x <= cx + r; x++) {
        const d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
        if (d <= r && d >= r - w) set(x, y, color);
      }
    }
  };
  const triangle = (x0, y0, x1, y1, x2, y2, color) => {
    const minX = Math.min(x0, x1, x2);
    const maxX = Math.max(x0, x1, x2);
    const minY = Math.min(y0, y1, y2);
    const maxY = Math.max(y0, y1, y2);
    const area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
    for (let y = minY; y <= maxY; y++) {
      for (let x = minX; x <= maxX; x++) {
        const w0 = (x1 - x0) * (y - y0) - (y1 - y0) * (x - x0);
        const w1 = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1);
        const w2 = (x0 - x2) * (y - y2) - (y0 - y2) * (x - x2);
        const hasNeg = w0 < 0 || w1 < 0 || w2 < 0;
        const hasPos = w0 > 0 || w1 > 0 || w2 > 0;
        if (!(hasNeg && hasPos) && area !== 0) set(x, y, color);
      }
    }
  };
  const line = (x0, y0, x1, y1, w, color) => {
    const steps = Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2);
    for (let i = 0; i <= steps; i++) {
      circle(x0 + ((x1 - x0) * i) / steps, y0 + ((y1 - y0) * i) / steps, w / 2, color);
    }
  };
  return { px, set, rect, circle, ring, triangle, line };
}

/** 工作台：房子（屋顶 + 屋身 + 门窗） */
function drawWorkbench(c, color) {
  c.triangle(40, 12, 10, 38, 70, 38, color);
  c.rect(18, 38, 62, 68, color);
  c.rect(34, 48, 46, 68, [0, 0, 0, 0]);
  c.rect(24, 46, 30, 52, [0, 0, 0, 0]);
  c.rect(50, 46, 56, 52, [0, 0, 0, 0]);
  c.rect(36, 50, 44, 68, color);
}

/** 审批：文档 + 对勾 */
function drawApproval(c, color) {
  c.rect(18, 8, 62, 72, color);
  c.rect(26, 18, 54, 24, [0, 0, 0, 0]);
  c.rect(26, 30, 54, 36, [0, 0, 0, 0]);
  c.rect(22, 44, 58, 70, [0, 0, 0, 0]);
  c.line(27, 58, 36, 67, 8, color);
  c.line(36, 67, 54, 46, 8, color);
}

/** 考勤：时钟 */
function drawAttendance(c, color) {
  c.ring(40, 40, 28, 6, color);
  c.rect(38, 22, 42, 42, color);
  c.rect(40, 38, 56, 42, color);
}

/** 我的：人形（头 + 肩部圆顶） */
function drawMine(c, color) {
  c.circle(40, 28, 14, color);
  for (let y = 52; y <= 78; y++) {
    for (let x = 0; x < SIZE; x++) {
      const dx = x - 40;
      const dy = y - 88;
      if (dx * dx + dy * dy <= 36 * 36) c.set(x, y, color);
    }
  }
  c.rect(10, 72, 70, 78, color);
}

const builders = {
  workbench: drawWorkbench,
  approval: drawApproval,
  attendance: drawAttendance,
  mine: drawMine
};

const NORMAL = [0x8a, 0x90, 0xa0, 0xff];
const ACTIVE = [0x2f, 0x6d, 0xf6, 0xff];

mkdirSync(OUT_DIR, { recursive: true });
for (const [name, draw] of Object.entries(builders)) {
  for (const [suffix, color] of [['', NORMAL], ['-active', ACTIVE]]) {
    const c = canvas();
    draw(c, color);
    writeFileSync(join(OUT_DIR, `${name}${suffix}.png`), encodePng(SIZE, SIZE, c.px));
    console.log(`generated ${name}${suffix}.png`);
  }
}
