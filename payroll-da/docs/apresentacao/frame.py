import sys
from PIL import Image, ImageDraw, ImageFilter
src, out, w_in = sys.argv[1], sys.argv[2], float(sys.argv[3])
im = Image.open(src).convert('RGBA'); W, H = im.size; ppi = W / w_in
pad = round(0.06*ppi); m = round(0.16*ppi); r = round(0.12*ppi); off = round(0.03*ppi)
CW, CH = W + 2*pad + 2*m, H + 2*pad + 2*m
canvas = Image.new('RGBA', (CW, CH), (0,0,0,0))
sh = Image.new('L', (CW, CH), 0); ImageDraw.Draw(sh).rounded_rectangle([m, m+off, m+W+2*pad, m+off+H+2*pad], r, fill=60)
sh = sh.filter(ImageFilter.GaussianBlur(round(0.06*ppi)))
canvas.paste(Image.new('RGBA', (CW, CH), (17,20,51,255)), (0,0), sh)
card = Image.new('L', (CW, CH), 0); ImageDraw.Draw(card).rounded_rectangle([m, m, m+W+2*pad, m+H+2*pad], r, fill=255)
canvas.paste(Image.new('RGBA', (CW, CH), (255,255,255,255)), (0,0), card)
mask = Image.new('L', (W, H), 0); ImageDraw.Draw(mask).rounded_rectangle([0,0,W-1,H-1], max(2, round(0.05*ppi)), fill=255)
canvas.paste(im, (m+pad, m+pad), mask)
canvas.save(out)
print(pad/ppi + m/ppi)
