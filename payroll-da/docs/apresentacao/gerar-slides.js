const pptxgen = require('pptxgenjs');
const React = require('react'); const RDS = require('react-dom/server'); const sharp = require('sharp');
const fa = require('react-icons/fa');
const { applyTheme } = require(process.env.SKILL + '/scripts/apply_theme.js');
const IMG = process.env.S + '/img/';
const OUT = process.argv[2];
const THEME = { name: 'PayRoll-DA', headFontFace: 'Arial', bodyFontFace: 'Calibri',
  colors: { dk1: '111433', lt1: 'FFFFFF', dk2: '1E2250', lt2: 'F3F4FB', accent1: '5B5BF6', accent2: '10B981',
    accent3: 'F59E0B', accent4: 'F97316', accent5: 'EF4444', accent6: '8B8FB8', hlink: '5B5BF6', folHlink: '8B5CF6' } };
const HEX = { navy: '111433', indigo: '5B5BF6', green: '10B981', amber: 'F59E0B', orange: 'F97316', red: 'EF4444', muted: '6B7091', lt2: 'F3F4FB', white: 'FFFFFF', ice: 'C7C9F7' };
async function icon(Comp, color) {
  const svg = RDS.renderToStaticMarkup(React.createElement(Comp, { color: '#' + color, size: 256 }));
  const buf = await sharp(Buffer.from(svg)).png().toBuffer();
  return 'image/png;base64,' + buf.toString('base64');
}
const MEMBERS = ['Arthur Folly Nagib Ferreira', 'Gustavo Erthal Leão de Siqueira', 'João Pedro de Conto', 'João Vitor Erthal de Siqueira', 'Rafael Eduardo Machado'];
(async () => {
  const pres = new pptxgen(); pres.layout = 'LAYOUT_16x9';
  pres.title = 'PayRoll-DA'; pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
  const C = pres.SchemeColor;
  pres.defineSlideMaster({ title: 'Escuro', background: { color: C.text1 }, objects: [] });
  pres.defineSlideMaster({ title: 'Claro', background: { color: C.background2 },
    objects: [{ placeholder: { options: { name: 'title', type: 'title', x: 0.5, y: 0.3, w: 9, h: 0.7, fontSize: 30, bold: true, color: C.text1, fontFace: THEME.headFontFace, margin: 0, valign: 'middle', align: 'left' }, text: '' } }],
    slideNumber: { x: 9.3, y: 5.25, w: 0.4, h: 0.25, fontSize: 10, color: C.accent6 } });
  const shadow = () => ({ type: 'outer', color: '111433', opacity: 0.18, blur: 8, offset: 2, angle: 90 });
  const card = (s, x, y, w, h, fill, name) => s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.12, fill: { color: fill || C.background1 }, line: { color: 'E1E3F0', width: 0.75 }, objectName: name });
  const shot = (s, file, x, y, w, h, alt, name) => {
    const out = IMG + 'framed-' + file;
    const fs = require('fs');
    // print já emoldurado (tirado da versão anterior do PPTX): a moldura do frame.py soma 0,22" de cada lado
    const e = !fs.existsSync(IMG + file) && fs.existsSync(out) ? 0.22
      : parseFloat(require('child_process').execFileSync('python3', [__dirname + '/frame.py', IMG + file, out, String(w)]).toString());
    s.addImage({ path: out, x: x - e, y: y - e, w: w + 2 * e, h: h + 2 * e, altText: alt, objectName: name });
  };
  const BG = { [C.accent1]: HEX.indigo, [C.background1]: HEX.white, [C.accent2]: HEX.green };
  const badge = async (s, Comp, x, y, d, bg, fg, name) => {
    const inner = RDS.renderToStaticMarkup(React.createElement(Comp, { color: '#' + fg, size: 256 })).replace('<svg ', '<svg x="128" y="128" ');
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"><circle cx="256" cy="256" r="254" fill="#' + (BG[bg] || bg) + '"/>' + inner + '</svg>';
    const buf = await sharp(Buffer.from(svg)).png().toBuffer();
    s.addImage({ data: 'image/png;base64,' + buf.toString('base64'), x, y, w: d, h: d, altText: name, objectName: name });
  };
  const T = (s, text, o) => s.addText(text, Object.assign({ isTextBox: true, margin: 0, fontFace: THEME.bodyFontFace }, o));

  // 1 Capa
  pres.addSection({ title: 'Capa' });
  let s = pres.addSlide({ masterName: 'Escuro', sectionTitle: 'Capa' });
  await badge(s, fa.FaChartBar, 0.6, 0.55, 0.55, C.accent1, HEX.white, 'Logo');
  T(s, [{ text: 'Payroll', options: { color: C.background1 } }, { text: ' DA', options: { color: HEX.ice } }], { x: 1.3, y: 0.55, w: 3, h: 0.55, fontSize: 22, bold: true, fontFace: THEME.headFontFace, valign: 'middle' });
  T(s, 'Despesa com pessoal das prefeituras sob controle', { x: 0.6, y: 1.4, w: 4.6, h: 0.95, fontSize: 26, bold: true, color: C.background1, fontFace: THEME.headFontFace, valign: 'top' });
  T(s, 'Dashboard que consulta os dados oficiais, grava no banco e mostra cada município frente aos limites da LRF', { x: 0.6, y: 2.45, w: 4.4, h: 0.65, fontSize: 14, color: HEX.ice, valign: 'top' });
  T(s, 'EQUIPE', { x: 0.6, y: 3.35, w: 3, h: 0.25, fontSize: 11, bold: true, color: C.accent2, charSpacing: 2 });
  T(s, MEMBERS.map((m, i) => ({ text: m, options: { breakLine: i < MEMBERS.length - 1 } })), { x: 0.6, y: 3.65, w: 4.5, h: 1.35, fontSize: 13, color: C.background1, valign: 'top', paraSpaceAfter: 2 });
  s.addImage({ path: IMG + 'login-novo.png', x: 5.45, y: 1.35, w: 4.15, h: 2.594, altText: 'Tela de login do PayRoll-DA', objectName: 'Print login' });
  T(s, 'Tela de login da versão atual', { x: 5.45, y: 4.05, w: 4.15, h: 0.3, fontSize: 11, color: HEX.ice, italic: true });

  // 2 Ideia principal
  pres.addSection({ title: 'Ideia principal' });
  s = pres.addSlide({ masterName: 'Claro', sectionTitle: 'Ideia principal' });
  s.addText('Ideia principal', { placeholder: 'title' });
  T(s, 'As prefeituras e câmaras precisam acompanhar se a despesa com pessoal está dentro do limite da Lei de Responsabilidade Fiscal. O PayRoll-DA automatiza esse acompanhamento.', { x: 0.5, y: 1.0, w: 9, h: 0.6, fontSize: 14, color: HEX.muted, valign: 'top' });
  const steps = [
    [fa.FaSearch, '1. Analisa', 'Com o município e o ano, busca o RGF da despesa com pessoal no SIMAM do TCE-PR'],
    [fa.FaDatabase, '2. Grava', 'Salva os dados no PostgreSQL, por município, entidade e período'],
    [fa.FaChartPie, '3. Exibe', 'Mostra no dashboard a situação de cada entidade frente ao limite']];
  for (let i = 0; i < 3; i++) {
    const x = 0.5 + i * 3.1;
    card(s, x, 1.8, 2.8, 1.75, null, 'Etapa ' + (i + 1));
    await badge(s, steps[i][0], x + 0.25, 2.0, 0.55, C.accent1, HEX.white, 'Ícone etapa ' + (i + 1));
    T(s, steps[i][1], { x: x + 0.95, y: 2.0, w: 1.7, h: 0.55, fontSize: 18, bold: true, color: C.text1, valign: 'middle', fontFace: THEME.headFontFace });
    T(s, steps[i][2], { x: x + 0.25, y: 2.7, w: 2.35, h: 0.75, fontSize: 13, color: HEX.muted, valign: 'top' });
    if (i < 2) s.addImage({ data: await icon(fa.FaChevronRight, HEX.indigo), x: x + 2.86, y: 2.55, w: 0.18, h: 0.25, altText: 'seta', objectName: 'Seta ' + (i + 1) });
  }
  const stats = [['54%', 'limite do Executivo', C.accent1], ['6%', 'limite do Legislativo', C.accent1], ['4 faixas', 'Regular · Alerta · Prudencial · Acima', C.accent2], ['2 perfis', 'Administrador e Prefeitura', C.accent2]];
  for (let i = 0; i < 4; i++) {
    const x = 0.5 + i * 2.3;
    T(s, stats[i][0], { x, y: 3.85, w: 2.1, h: 0.6, fontSize: 30, bold: true, color: stats[i][2], fontFace: THEME.headFontFace });
    T(s, stats[i][1], { x, y: 4.45, w: 2.1, h: 0.5, fontSize: 12, color: HEX.muted, valign: 'top' });
  }
  T(s, 'Limites calculados sobre a Receita Corrente Líquida (RCL), conforme a LRF', { x: 0.5, y: 5.2, w: 8, h: 0.25, fontSize: 10, italic: true, color: HEX.muted });

  // 3 Protótipo
  pres.addSection({ title: 'Protótipo e Teste' });
  s = pres.addSlide({ masterName: 'Claro', sectionTitle: 'Protótipo e Teste' });
  s.addText('Protótipo: as primeiras versões', { placeholder: 'title' });
  shot(s, 'v1-dashboard.png', 0.56, 1.2, 4.28, 1.972, 'Primeira versão do dashboard', 'Print v1 dashboard');
  T(s, 'Primeiro dashboard, ainda com dados de exemplo', { x: 0.56, y: 3.28, w: 4.28, h: 0.25, fontSize: 11, italic: true, color: HEX.muted });
  shot(s, 'v1-consulta.png', 5.16, 1.2, 4.28, 1.976, 'Primeira versão da consulta automática', 'Print v1 consulta');
  T(s, 'Primeira tela da consulta automática ao SIMAM', { x: 5.16, y: 3.28, w: 4.28, h: 0.25, fontSize: 11, italic: true, color: HEX.muted });
  T(s, 'TECNOLOGIAS', { x: 0.5, y: 3.7, w: 3.8, h: 0.25, fontSize: 11, bold: true, color: C.accent1, charSpacing: 2 });
  const techs = [[fa.FaJava, 'Java 21 + Spring Boot 3.3', 'back-end e API'], [fa.FaDatabase, 'PostgreSQL + Flyway', 'banco e migrações'],
    [fa.FaReact, 'React 18 + Vite', 'front-end'], [fa.FaChartLine, 'Recharts', 'gráficos do dashboard'],
    [fa.FaRobot, 'Groq (Llama 3.3 70B)', 'assistente IA'], [fa.FaDocker, 'Docker + Caddy', 'implantação com HTTPS']];
  for (let i = 0; i < techs.length; i++) {
    const x = 0.5 + (i % 3) * 3.05, y = 4.05 + Math.floor(i / 3) * 0.62;
    await badge(s, techs[i][0], x, y, 0.46, C.background1, HEX.indigo, 'Ícone ' + techs[i][1]);
    T(s, techs[i][1], { x: x + 0.58, y: y - 0.02, w: 2.4, h: 0.26, fontSize: 13, bold: true, color: C.text1 });
    T(s, techs[i][2], { x: x + 0.58, y: y + 0.24, w: 2.4, h: 0.22, fontSize: 11, color: HEX.muted });
  }

  // 4 Teste
  s = pres.addSlide({ masterName: 'Claro', sectionTitle: 'Protótipo e Teste' });
  s.addText('Teste: cada integrante validou uma área', { placeholder: 'title' });
  shot(s, 'teste-erro.png', 0.56, 1.2, 3.7, 1.999, 'Teste da consulta ao SIMAM com erro tratado', 'Print teste erro');
  T(s, 'Teste real no SIMAM: falha tratada com mensagem clara e opção de importar CSV', { x: 0.56, y: 3.33, w: 3.7, h: 0.45, fontSize: 11, italic: true, color: HEX.muted, valign: 'top' });
  const area = [['Gustavo Erthal Leão de Siqueira', 'Sistema completo (todas as telas)'], ['Rafael Eduardo Machado', 'Dashboard'], ['João Vitor Erthal de Siqueira', 'Consulta automática'],
    ['João Pedro de Conto', 'Assistente IA'], ['Arthur Folly Nagib Ferreira', 'Usuários / Minha conta']];
  card(s, 4.6, 1.14, 4.84, 2.66, null, 'Cartão responsáveis');
  for (let i = 0; i < area.length; i++) {
    const y = 1.3 + i * 0.49;
    await badge(s, fa.FaCheck, 4.8, y + 0.02, 0.36, C.accent2, HEX.white, 'Ok ' + (i + 1));
    T(s, area[i][0], { x: 5.3, y, w: 4.0, h: 0.24, fontSize: 13, bold: true, color: C.text1 });
    T(s, area[i][1], { x: 5.3, y: y + 0.22, w: 4.0, h: 0.22, fontSize: 12, color: HEX.muted });
  }
  const tstats = [['6', 'roteiros de teste, um por tela'], ['16', 'testes automatizados no back-end'], ['5', 'relatórios de teste da equipe']];
  for (let i = 0; i < 3; i++) {
    const x = 0.56 + i * 3.05;
    T(s, tstats[i][0], { x, y: 4.2, w: 0.85, h: 0.7, fontSize: 36, bold: true, color: C.accent1, fontFace: THEME.headFontFace, valign: 'middle' });
    T(s, tstats[i][1], { x: x + 0.95, y: 4.25, w: 1.9, h: 0.6, fontSize: 13, color: HEX.muted, valign: 'middle' });
  }

  // 5 Produto - dashboard
  pres.addSection({ title: 'Apresentação do produto' });
  s = pres.addSlide({ masterName: 'Escuro', sectionTitle: 'Apresentação do produto' });
  T(s, 'O produto: dashboard', { x: 0.5, y: 0.3, w: 9, h: 0.7, fontSize: 30, bold: true, color: C.background1, fontFace: THEME.headFontFace, valign: 'middle' });
  s.addImage({ path: IMG + 'dashboard-crop.png', x: 0.5, y: 1.2, w: 5.92, h: 3.7, altText: 'Dashboard com a análise do município de Jussara', objectName: 'Print dashboard' });
  T(s, 'Exemplo com os valores anuais de 2019 a 2025 do relatório de Jussara-PR', { x: 0.5, y: 4.98, w: 5.92, h: 0.25, fontSize: 10, italic: true, color: HEX.ice });
  const feats = [['Análise do município', 'RCL, despesa e índice da LRF comparados ao ano anterior'], ['Série histórica', 'crescimento acumulado e médio da receita e da despesa'],
    ['Limites da LRF', 'índice por ano contra alerta, prudencial e máximo'], ['Visão geral', 'faixas, ranking e tabela de todas as entidades']];
  for (let i = 0; i < feats.length; i++) {
    const y = 1.2 + i * 0.93;
    T(s, feats[i][0], { x: 6.75, y, w: 2.85, h: 0.3, fontSize: 15, bold: true, color: HEX.ice });
    T(s, feats[i][1], { x: 6.75, y: y + 0.3, w: 2.85, h: 0.55, fontSize: 12, color: C.background1, valign: 'top' });
  }

  // 6 Produto - consulta e assistente
  s = pres.addSlide({ masterName: 'Claro', sectionTitle: 'Apresentação do produto' });
  s.addText('O produto: analisar município e assistente IA', { placeholder: 'title' });
  shot(s, 'analisar-municipio.png', 0.55, 1.25, 4.25, 2.485, 'Tela Analisar município, com município, relatório e ano', 'Print consulta');
  shot(s, 'assistente-crop.png', 5.2, 1.25, 4.25, 2.485, 'Assistente IA com histórico de conversas', 'Print assistente');
  await badge(s, fa.FaSearch, 0.55, 4.0, 0.46, C.accent1, HEX.white, 'Ícone consulta');
  T(s, 'Analisar município', { x: 1.15, y: 3.97, w: 3.6, h: 0.28, fontSize: 15, bold: true, color: C.text1 });
  T(s, 'Município, relatório e ano: o sistema escolhe a entidade e importa o ano todo', { x: 1.15, y: 4.27, w: 3.65, h: 0.55, fontSize: 12, color: HEX.muted, valign: 'top' });
  await badge(s, fa.FaRobot, 5.2, 4.0, 0.46, C.accent1, HEX.white, 'Ícone assistente');
  T(s, 'Assistente IA', { x: 5.8, y: 3.97, w: 3.6, h: 0.28, fontSize: 15, bold: true, color: C.text1 });
  T(s, 'Responde com os dados do banco, sugere filtros no dashboard e guarda o histórico', { x: 5.8, y: 4.27, w: 3.65, h: 0.55, fontSize: 12, color: HEX.muted, valign: 'top' });

  // 7 Produto - professores
  s = pres.addSlide({ masterName: 'Claro', sectionTitle: 'Apresentação do produto' });
  s.addText('O produto: tabela dos professores', { placeholder: 'title' });
  shot(s, 'professores.png', 0.55, 1.25, 5.6, 3.272, 'Tela dos professores com a tabela de vencimentos de Jussara', 'Print professores');
  const prof = [['R$ 2.565,32', 'piso do magistério em 2026, para 20 horas', C.accent1], ['14,69%', 'defasagem da tabela de Jussara em relação ao piso', C.accent5], ['3 tabelas', 'atual, em conformidade com o piso e defasagem mensal', C.accent2]];
  for (let i = 0; i < prof.length; i++) {
    const y = 1.2 + i * 1.12;
    T(s, prof[i][0], { x: 6.6, y, w: 2.9, h: 0.55, fontSize: 30, bold: true, color: prof[i][2], fontFace: THEME.headFontFace, valign: 'middle' });
    T(s, prof[i][1], { x: 6.6, y: y + 0.55, w: 2.9, h: 0.45, fontSize: 12, color: HEX.muted, valign: 'top' });
  }
  T(s, 'Exemplo: Jussara-PR, conforme o relatório de análise do PSPN 2026', { x: 0.55, y: 4.75, w: 5.6, h: 0.25, fontSize: 10, italic: true, color: HEX.muted });

  // 8 Obrigado
  pres.addSection({ title: 'Finalização' });
  s = pres.addSlide({ masterName: 'Escuro', sectionTitle: 'Finalização' });
  await badge(s, fa.FaChartBar, 4.65, 0.6, 0.7, C.accent1, HEX.white, 'Logo final');
  T(s, 'Obrigado!', { x: 0.5, y: 1.45, w: 9, h: 0.9, fontSize: 48, bold: true, color: C.background1, align: 'center', fontFace: THEME.headFontFace, valign: 'middle' });
  T(s, 'PayRoll-DA · despesa com pessoal das prefeituras frente à LRF', { x: 0.5, y: 2.35, w: 9, h: 0.35, fontSize: 14, color: HEX.ice, align: 'center' });
  T(s, 'EQUIPE', { x: 0.5, y: 3.0, w: 9, h: 0.25, fontSize: 11, bold: true, color: C.accent2, align: 'center', charSpacing: 2 });
  T(s, [{ text: MEMBERS.slice(0, 3).join('   ·   '), options: { breakLine: true } }, { text: MEMBERS.slice(3).join('   ·   ') }], { x: 0.5, y: 3.35, w: 9, h: 0.75, fontSize: 14, color: C.background1, align: 'center', valign: 'top', paraSpaceAfter: 4 });
  T(s, 'Perguntas?', { x: 0.5, y: 4.55, w: 9, h: 0.4, fontSize: 16, italic: true, color: HEX.ice, align: 'center' });

  await pres.writeFile({ fileName: OUT });
  await applyTheme(OUT, THEME);
  console.log('ok');
})().catch(e => { console.error(e); process.exit(1); });
