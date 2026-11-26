# KraftCity 1.1.35 — rotina do Padeiro

O Padeiro visita baús de descarte vinculados a postos de Fazendeiro. A procura usa o alcance `comercio.padeiroAlcanceColeta`, com padrão de 32 blocos medidos a partir do posto e intervalo configurável de 4 a 128. A procura considera apenas recipientes carregados e repete a busca a cada dois segundos quando não encontra trigo acessível. Baús comuns, baús com armadilha e barris são fontes compatíveis.

| Nível | Trigo acumulado por dia | Pães por dia |
| --- | ---: | ---: |
| 1 | 15 | 5 |
| 2 | 30 | 10 |
| 3 | 45 | 15 |
| 4 | 60 | 20 |
| 5 | 75 | 25 |

A cota é persistida no trabalhador e registra cada trigo efetivamente transferido. O novo limite passa a valer quando o trabalhador sobe de nível e desconta a coleta já registrada. O expediente usa o início de dia profissional existente às 23000 marcas do relógio do mundo. Voltar o relógio ou reabrir o mundo no mesmo dia mantém o saldo registrado.

O Padeiro usa o próprio posto para cada receita de três trigos e um pão e mantém a duração comercial de cada nível. Entregas menores que três trigos acumulam no inventário. Ao fim de um lote, o trabalhador entrega os pães no próprio recipiente vinculado e retorna ao posto. Se o recipiente estiver indisponível ou cheio, o inventário conserva os pães. A interface mostra a cota de trigo e permite vincular o recipiente de saída.
