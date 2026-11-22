# KraftCity 1.1.25

Base 1.1.24 na branch `feature/kraftcity-builder`, commit `48a5e7506c3fe456c9f58e861a495ac863dc2b6f`. Requer Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Interface e insumos

Fazendeiro e Apicultor compartilham o menu de duas páginas. A primeira centraliza os três insumos, os cinco tamanhos de área e os dois inventários. A segunda contém demissão com confirmação, vínculo de baú especial e limites. O botão de página preserva a mesma sessão do menu e seu inventário de servidor.

O Apicultor aceita tesouras que oferecem a ação Forge `shears_harvest`, frascos de vidro vazios e flores do grupo vanilla. O grupo de itens `residenciasaldeoes:apiary_bottles` permite cadastrar frascos vazios compatíveis por pacote de dados. Os itens na configuração anterior migram quando há espaço. Os insumos, o XP e os produtos continuam no posto compartilhado.

## Território e apicultura

A área tem cinco camadas, de Y+1 a Y+5 e lados de 5, 7, 9, 11 ou 13 blocos. Os níveis desbloqueiam os tamanhos para seleção manual. Um novo trabalhador começa em 5×5×5. A recuperação do mesmo trabalhador conserva a escolha. Os limites azuis usam a mesma altura da área do servidor.

O apiário registra de uma a cinco colmeias, naturais ou artesanais, e de três a quinze abelhas vinculadas conforme o nível. A identidade do posto e uma identidade própria de cada abelha são gravadas separadamente no NBT. A identidade própria acompanha a abelha dentro da colmeia mesmo quando o Minecraft recria seu UUID de entidade. Morte libera uma vaga, e descarregar a entidade ou seu chunk conserva o registro. Colmeias removidas em áreas carregadas liberam sua vaga. Postos sobrepostos respeitam os registros de colmeias entre si.

O Apicultor apresenta a flor, se aproxima e consome de uma a cinco flores. O contador da tentativa é salvo na abelha. O controle territorial orienta abelhas vinculadas que se afastam mais de sete blocos dos limites com a navegação de voo. A reprodução utiliza o estado de cortejo vanilla e reserva uma vaga em cada tentativa profissional. O nascimento natural confirma o vínculo do filhote à mesma estação. A capacidade aumenta com o nível, e as abelhas e colmeias provêm do mundo.

## Produção e rotina

O servidor verifica nível de mel, ocupação da colmeia, permissão, alcance, visibilidade e espaço no inventário. Com frasco vazio, recolhe um frasco de mel. Sem frasco, uma tesoura válida recolhe três favos com desgaste. O servidor simula a troca antes de baixar o nível de mel. Abelhas do apiário ficam protegidas da reação à coleta profissional. A reação de abelhas alheias permanece possível, e as interações vanilla de jogadores com colmeias seguem o jogo base.

A apicultura usa a coleta física, o descarte ao fim do expediente, a navegação, as portas e os portões da rotina industrial. Durante trovões, aplica a circulação residencial compartilhada. O histórico profissional, a área e o inventário são guardados pelo posto. O vínculo residencial, o sono e a cura seguem o sistema residencial.

## Evidência

Os GameTests cobrem filtros e inventário compartilhado, áreas e colmeias, flores consumidas, capacidade, produção com frascos ou tesoura, inventário cheio, reprodução vanilla e vínculo da cria. As rotas são exercitadas com uma colmeia atrás de uma parede, uma tempestade que interrompe o trabalho e um descarte seguido de caminhada até a cama a 20 blocos. A suíte gráfica exercita as duas páginas e verifica os dados do apiário após reabrir o mundo em outra JVM.
