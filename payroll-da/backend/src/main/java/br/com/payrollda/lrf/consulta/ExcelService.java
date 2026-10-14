package br.com.payrollda.lrf.consulta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import br.com.payrollda.lrf.dashboard.DashboardDtos.Demonstrativo;
import br.com.payrollda.lrf.dashboard.DashboardDtos.PontoEvolucao;

/** Monta a planilha do demonstrativo no layout do RGF, com a evolução mensal numa segunda aba. */
@Service
public class ExcelService {

    private static final String[] MESES = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};

    public byte[] gerar(Demonstrativo d, List<PontoEvolucao> evolucao) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            Sheet s = wb.createSheet("Demonstrativo");
            int r = 0;
            texto(s, r++, e.titulo, "RGF - Demonstrativo da Despesa com Pessoal");
            texto(s, r++, null, d.entidade() + " · " + d.municipio() + " (IBGE " + d.codIbge() + ")");
            texto(s, r++, null, "Período: 12 meses até " + MESES[d.mes() - 1] + "/" + d.ano()
                    + " · Poder " + (d.poder().equals("EXECUTIVO") ? "Executivo" : "Legislativo"));
            r++;
            Row cab = s.createRow(r++);
            celula(cab, 0, "Despesa com pessoal", e.cabecalho);
            celula(cab, 1, "Valor (R$)", e.cabecalho);

            r = linha(s, r, "Despesa bruta com pessoal (I)", d.despesaBruta(), e.moeda, e.normal);
            r = linha(s, r, "   Pessoal ativo", d.pessoalAtivo(), e.moeda, e.normal);
            r = linha(s, r, "   Pessoal inativo e pensionistas", d.inativosPensionistas(), e.moeda, e.normal);
            r = linha(s, r, "   Outras despesas de pessoal (terceirização)", d.outrasDespesas(), e.moeda, e.normal);
            r = linha(s, r, "Despesas não computadas (II)", d.naoComputadas(), e.moeda, e.normal);
            r = linha(s, r, "Despesa total com pessoal - DTP (III = I - II)", d.despesaTotal(), e.moedaNegrito, e.negrito);
            r++;
            r = linha(s, r, "Receita corrente líquida ajustada (IV)", d.rclAjustada(), e.moeda, e.normal);
            r = linha(s, r, "DTP sobre a RCL ajustada (III / IV)", pct(d.percentual()), e.percentual, e.negrito);
            r = linha(s, r, "Limite máximo", pct(d.limiteMaximo()), e.percentual, e.normal);
            r = linha(s, r, "Limite prudencial (95% do máximo)", pct(d.limiteMaximo().multiply(new BigDecimal("0.95"))), e.percentual, e.normal);
            r = linha(s, r, "Limite de alerta (90% do máximo)", pct(d.limiteMaximo().multiply(new BigDecimal("0.90"))), e.percentual, e.normal);
            r = linha(s, r, "Margem até o limite máximo (R$)",
                    d.rclAjustada().multiply(d.limiteMaximo()).divide(BigDecimal.valueOf(100)).subtract(d.despesaTotal()),
                    e.moeda, e.normal);
            r++;
            texto(s, r, e.negrito, "Situação: " + situacao(d.faixa()));
            s.setColumnWidth(0, 52 * 256);
            s.setColumnWidth(1, 22 * 256);

            Sheet ev = wb.createSheet("Evolução");
            Row h = ev.createRow(0);
            String[] titulos = {"Período", "Despesa total com pessoal", "RCL ajustada", "% da RCL", "Limite máximo"};
            for (int i = 0; i < titulos.length; i++) {
                celula(h, i, titulos[i], e.cabecalho);
                ev.setColumnWidth(i, (i == 0 ? 12 : 26) * 256);
            }
            int i = 1;
            for (PontoEvolucao p : evolucao) {
                Row row = ev.createRow(i++);
                row.createCell(0).setCellValue(MESES[p.mes() - 1] + "/" + p.ano());
                numero(row, 1, p.despesaTotal(), e.moeda);
                numero(row, 2, p.rclAjustada(), e.moeda);
                numero(row, 3, pct(p.percentual()), e.percentual);
                numero(row, 4, pct(p.limiteMaximo()), e.percentual);
            }
            ev.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String situacao(String faixa) {
        return switch (faixa) {
            case "ACIMA_LIMITE" -> "acima do limite máximo da LRF";
            case "PRUDENCIAL" -> "acima do limite prudencial";
            case "ALERTA" -> "acima do limite de alerta";
            default -> "dentro dos limites";
        };
    }

    /** O Excel guarda percentual como fração: 54% = 0,54. */
    private static BigDecimal pct(BigDecimal valor) {
        return valor.divide(BigDecimal.valueOf(100));
    }

    private static int linha(Sheet s, int r, String rotulo, BigDecimal valor, CellStyle estiloValor, CellStyle estiloRotulo) {
        Row row = s.createRow(r);
        celula(row, 0, rotulo, estiloRotulo);
        numero(row, 1, valor, estiloValor);
        return r + 1;
    }

    private static void texto(Sheet s, int r, CellStyle estilo, String valor) {
        celula(s.createRow(r), 0, valor, estilo);
    }

    private static void celula(Row row, int col, String valor, CellStyle estilo) {
        Cell c = row.createCell(col);
        c.setCellValue(valor);
        if (estilo != null) {
            c.setCellStyle(estilo);
        }
    }

    private static void numero(Row row, int col, BigDecimal valor, CellStyle estilo) {
        Cell c = row.createCell(col);
        c.setCellValue(valor == null ? 0 : valor.doubleValue());
        c.setCellStyle(estilo);
    }

    private static final class Estilos {
        final CellStyle titulo, cabecalho, normal, negrito, moeda, moedaNegrito, percentual;

        Estilos(XSSFWorkbook wb) {
            Font fTitulo = wb.createFont();
            fTitulo.setBold(true);
            fTitulo.setFontHeightInPoints((short) 14);
            Font fNegrito = wb.createFont();
            fNegrito.setBold(true);
            Font fBranca = wb.createFont();
            fBranca.setBold(true);
            fBranca.setColor(IndexedColors.WHITE.getIndex());

            titulo = wb.createCellStyle();
            titulo.setFont(fTitulo);
            cabecalho = wb.createCellStyle();
            cabecalho.setFont(fBranca);
            cabecalho.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            normal = wb.createCellStyle();
            negrito = wb.createCellStyle();
            negrito.setFont(fNegrito);
            negrito.setBorderTop(BorderStyle.THIN);

            short fmtMoeda = wb.createDataFormat().getFormat("#,##0.00");
            short fmtPct = wb.createDataFormat().getFormat("0.00%");
            moeda = wb.createCellStyle();
            moeda.setDataFormat(fmtMoeda);
            moedaNegrito = wb.createCellStyle();
            moedaNegrito.setDataFormat(fmtMoeda);
            moedaNegrito.setFont(fNegrito);
            moedaNegrito.setBorderTop(BorderStyle.THIN);
            percentual = wb.createCellStyle();
            percentual.setDataFormat(fmtPct);
        }
    }
}
