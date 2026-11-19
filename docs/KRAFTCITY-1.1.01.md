# KraftCity 1.1.01 — Fazendeiro

Base validada `d6982bd3dea9119e00bbb03a8f1f5acd2a2b3f2a`, branch `feature/kraftcity-builder`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

- A varredura linear anterior misturava colheita e plantio. O plantio novo usa espiral persistida, orientada pelo canto mais distante do trabalhador, e recalcula a orientação ao mudar o tamanho. Caminhos interrompidos têm limite de tentativa.
- O volume agrícola azul vai de Y+1 a Y+5. O suporte do cultivo pode estar imediatamente sob a base. A busca de itens estende três blocos nos eixos horizontais e exige navegação, visibilidade, autorização e capacidade.
- A colheita avalia a maturidade e preserva as culturas vizinhas. O trabalhador não pisa a terra arada de volta para terra. O replantio separa uma semente antes de aplicar o rendimento da enxada. Falta de semente deixa uma pendência da espécie original. Inventário cheio impede a colheita sem espaço.
- Cana usa água vanilla ou o balde do posto quando o solo é lateralmente adjacente. Uma coluna natural de três blocos rende apenas os dois segmentos superiores. O plantio de abóbora e melancia mantém uma posição lateral livre e a colheita procura o fruto associado ao caule.
- A coleta usa o inventário do posto. O descarte mantém reserva por espécie de até tamanho², preservando sementes de outras espécies quando uma pilha ocupa toda a primeira reserva. Recipientes vanilla são descobertos perto do limite. Recipientes Forge compatíveis podem ser associados explicitamente.
- Ao anoitecer, inclusive após mudança brusca de horário, o trabalhador tenta caminhar até o descarte antes do retorno residencial normal. A tentativa é limitada a 100 ticks por ciclo e preserva o conteúdo que o recipiente recusar.
- A enxada visual usa o modelo original em escala 2× no renderizador do aldeão. A escala não afeta o item no posto.
- O protocolo de emprego passa a v6 para sincronizar o tipo de limite. Use a mesma versão no cliente e no servidor. Os campos novos usam o estado industrial existente do posto.
