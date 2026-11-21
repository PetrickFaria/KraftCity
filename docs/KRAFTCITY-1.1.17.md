# KraftCity 1.1.17

A atualização parte da versão 1.1.16, commit `3b8bbe0b8a901f2e83d1bb5fc04393624ad237d0`, na branch `feature/kraftcity-builder`. Ambiente de referência com Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Ferramentas profissionais

| Ferramenta | Madeira | Pedra | Ferro | Ouro | Esmeralda | Diamante | Netherita |
|---|---|---|---|---|---|---|---|
| Espada | Vanilla | Vanilla | Vanilla | Vanilla | KraftCity | Vanilla | Vanilla |
| Machado | Vanilla | Vanilla | Vanilla | Vanilla | KraftCity | Vanilla | Vanilla |
| Enxada | Vanilla | Vanilla | Vanilla | Vanilla | KraftCity | Vanilla | Vanilla |
| Tesoura | KraftCity | **Nova** | Vanilla | KraftCity | KraftCity | KraftCity | KraftCity |
| Vara de pesca | Vanilla | **Nova** | KraftCity | KraftCity | KraftCity | KraftCity | KraftCity |

A aba Ferramentas KraftCity agrupa as cinco categorias e mantém essa ordem de materiais em cada uma. As armas de projétil e escudos continuam com suas regras e equipamentos próprios. O código de trabalho consome enxadas, machados, tesouras e varas de pesca, e o Soldado equipa espadas. As demais ferramentas exibidas pelas rotinas genéricas de animação, como a picareta, não constituem categorias com uso profissional específico.

- `residenciasaldeoes:stone_shears` usa duas pedras do grupo vanilla `minecraft:stone_tool_materials`, colocadas na diagonal. O modelo segue a tesoura vanilla, com acabamento cinza-pedra nas lâminas.
- `residenciasaldeoes:stone_fishing_rod` usa três pedras do mesmo grupo e duas linhas no formato de vara vanilla. A textura da haste usa cinza-pedra. O modelo com linha lançada mantém a linha e o anzol do modelo original.
- Ambas usam 131 pontos de durabilidade, o valor do tier de pedra vanilla, e aceitam reparo com o mesmo grupo de materiais da receita. O uso e o desgaste profissionais seguem as regras existentes do Pastoreiro, do Apicultor e do Pescador. A etapa não adiciona bônus, altera rendimentos ou troca receitas e IDs legados.
- Os testes cobrem o registro, a aba, receitas, texturas, a troca do modelo da vara no lançamento, a geometria na mão e nas costas dos trabalhadores, o uso nas profissões e a reabertura do mundo com desgaste persistente.

## Área militar

O Bloco de Soldado continua no centro da região de **201 × 31 × 201 blocos**. X e Z vão de −100 a +100 e Y vai de −15 a +15, incluindo os blocos das extremidades. Modos, movimentação, navegação, ameaças, combate e coleta usam o volume compartilhado. O volume é calculado do posto a cada uso, de modo que estações antigas recebem o novo limite ao carregar o mundo. O alcance local de ameaças continua sendo uma regra distinta da altura da região.

A validação cobre alvos em Y ±10, ±15 e ±16, nos três modos, e tentativas de movimento para fora pelas duas extremidades verticais. As regressões da reformulação militar, colheita, demais profissões e persistência continuam na suíte.
