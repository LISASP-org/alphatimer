// Renders the SVG sources in this directory into the PNG and ICO files of the AlphaTiming window and of the
// JAuswertung installer that bundles AlphaTiming (program icon, file association for .timing).
//
//   corepack enable   (once; provides the Yarn version pinned in package.json)
//   yarn install
//   yarn build
//
// Each icon has three sources: <name>-16.svg and <name>-32.svg use heavier strokes for small sizes,
// <name>.svg is used from 33px upwards. They follow the JAuswertung icons (jauswertung-files/src/icons).
import fs from 'node:fs';
import path from 'node:path';
import { Resvg } from '@resvg/resvg-js';

interface IcoFrame {
    size: number;
    png: Buffer;
}

const SRC = import.meta.dirname;
const OUT = path.join(SRC, '..', 'main', 'resources', 'org', 'lisasp', 'alphatiming', 'ui', 'icons', 'app');

function source(name: string, size: number): string {
    const suffix = size <= 16 ? '-16' : size <= 32 ? '-32' : '';
    return fs.readFileSync(path.join(SRC, `${name}${suffix}.svg`), 'utf8');
}

function render(svg: string, size: number): Buffer {
    return new Resvg(svg, { fitTo: { mode: 'width', value: size }, font: { loadSystemFonts: false } }).render().asPng();
}

// ICO with PNG-compressed frames (supported since Windows Vista)
function ico(frames: IcoFrame[]): Buffer {
    const header = Buffer.alloc(6 + 16 * frames.length);
    header.writeUInt16LE(1, 2);
    header.writeUInt16LE(frames.length, 4);
    let offset = header.length;
    frames.forEach(({ size, png }, i) => {
        const e = 6 + 16 * i;
        header[e] = size >= 256 ? 0 : size;
        header[e + 1] = size >= 256 ? 0 : size;
        header.writeUInt16LE(1, e + 4);
        header.writeUInt16LE(32, e + 6);
        header.writeUInt32LE(png.length, e + 8);
        header.writeUInt32LE(offset, e + 12);
        offset += png.length;
    });
    return Buffer.concat([header, ...frames.map(f => f.png)]);
}

const ICO_SIZES = [16, 24, 32, 48, 64, 256];
const icon = (name: string, size: number): Buffer => render(source(name, size), size);

fs.mkdirSync(OUT, { recursive: true });
for (const size of [16, 32, 48, 128, 256]) {
    fs.writeFileSync(path.join(OUT, `alphatiming-${size}.png`), icon('alphatiming', size));
}
fs.writeFileSync(path.join(OUT, 'alphatiming.ico'), ico(ICO_SIZES.map(size => ({ size, png: icon('alphatiming', size) }))));
fs.writeFileSync(path.join(OUT, 'timing-file.ico'), ico(ICO_SIZES.map(size => ({ size, png: icon('timing', size) }))));
console.log(`Wrote icons to ${OUT}`);
