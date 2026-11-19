# KraftCity 1.1.02 — Fazendeiro

Base validada `97040b0b52c0e3d9689df7f80017ab0b1397800c`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

- O posto persiste a orientação e os cursores do zigue-zague. A colheita madura tem precedência sobre o plantio. O plantio em solo pronto precede a aragem, que só ocorre para uma semente disponível. A coleta de itens tem prioridade operacional.
- Aragem, semeadura e colheita de culturas baixas exigem a coluna do alvo, visibilidade e proximidade. Cana e frutos podem ser trabalhados de um bloco lateral. Uma rota frustrada tem prazo de 100 ticks e o destino entra em espera por 100 ticks.
- A colheita de uma cultura madura mantém os suportes intactos. A reinicialização da idade da mesma cultura usa uma atualização localizada. A reserva preserva a espécie e o rendimento já definido para cada enxada.
- A área azul e a varredura agrícola cobrem Y+1 a Y+3. O solo pode ficar imediatamente sob a base. O aldeão ocioso procura uma posição caminhável na área quando estiver do lado de fora.
- Cana pode ser plantada em solo válido com água real ou com o balde do posto atuando como água virtual adjacente. Em colunas de três blocos, os dois segmentos superiores geram duas entidades de item recolhidas fisicamente.
- Caules maduros são verificados antes da semeadura. Espaço já válido é preservado. Obstáculos vegetais simples podem ser limpos no alvo; culturas existentes ficam protegidas. Se houver terra arada sob o obstáculo, o aldeão salta sobre a posição. Só essa aterrissagem específica pode pisotear o solo, conforme as regras vanilla. O salto tem até quatro tentativas.
- Antes de dormir, inclusive depois de uma mudança brusca de hora, a coleta recebe até 120 ticks dentro de uma janela total de 220 ticks que inclui o descarte. Itens sem acesso ou espaço ficam no mundo; sobras que o recipiente recusar ficam no posto. Depois da tentativa, a rotina residencial reassume o controle.
