# KraftCity 1.1.21

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base 1.1.20 em `f8371fa4b9d4e19cf5a315a2baff41fb74648492`, branch `feature/kraftcity-builder`.

## Ameaças e limites

A Blacklist e o combate compartilham a seleção pelos IDs do registro Forge. A aranha selecionada passava por um veto adicional quando estava passiva sob luz forte. Esse veto foi removido, inclusive para seleções salvas. O Creeper continua excluído por padrão, pode ser autorizado manualmente e mantém escolhas existentes. No ataque ainda são verificadas a presença do alvo, a proteção de aliados, a situação militar, o limite territorial e a possibilidade de alcançar ou atingir a entidade.

O volume inclusivo tem 101 × 31 × 101 blocos, com alcance horizontal de 50 blocos a partir do posto e vertical de 15. A IA, a validação de movimento, o saque e as faces vermelhas usam os mesmos limites. Postos existentes passam a usar o volume recalculado sem migração de IDs ou coordenadas.

Patrulhar consulta entidades carregadas em todo o volume a cada dez ticks, mesmo quando a ameaça está longe do Soldado. Um residente ou trabalhador pode ser protegido até dez blocos de distância dele. Áreas residenciais e profissionais são medidas a partir do volume físico, incluindo áreas salvas e obras ativas do Construtor, com margem de até cinco blocos. Uma ameaça protegida pode ser adquirida por uma rota válida apesar de ainda estar fora da linha de visão. O Soldado só ataca de uma posição que permita o golpe ou o disparo. A seleção prioriza a proteção de residentes e mantém a escolha do alvo próximo nas disputas locais à distância.

## Navegação e orientação

O caminho padrão usava o alcance interno de busca do aldeão. Em trajetos longos, o navegador agora recebe um alcance explícito, limitado, proporcional à distância e com orçamento maior de nós. A rota resultante usa apenas chunks carregados e pontos na região militar. Falhas de rota de longa distância são espaçadas e a aproximação tenta posições alternativas. A troca de modo invalida a navegação anterior. Guarnecer retorna ao posto mesmo de longe. Seguir mantém a distância de sete blocos do jogador dentro do território e usa um ponto interno quando o jogador sai.

A direção de combate é recalculada a cada tick a partir dos olhos do alvo. Cabeça e inclinação convergem para o alvo com passos limitados. O corpo acompanha quando necessário e a navegação mantém controle durante a perseguição de espada. Arco e besta preservam a preparação, o recuo, as verificações de linha de tiro e os respectivos tempos.

## Identificação e armaduras

A sincronização de atividade inclui a profissão vigente. O renderizador acrescenta uma linha centralizada ao nome vanilla, preservando o nome personalizado e a opacidade das etiquetas. A linha acompanha contratação, demissão e cura. Cliente e servidor usam o mesmo protocolo atualizado.

No modelo tradicional, o deslocamento de encaixe do capacete passa a acompanhar o eixo local da cabeça. O deslocamento no eixo da entidade fazia a peça perder o encaixe quando a cabeça girava ou inclinava. As pernas do aldeão vão de 0 a 12 unidades a partir do pivô. A malha vanilla da armadura acrescenta margem acima e abaixo desse intervalo. Calças e botas agora são ajustadas para que a extensão vertical caiba no segmento original, aplicando a correção no eixo da perna já articulada. O ajuste usa a mesma pose de perna ao caminhar, combater e dormir. O peitoral e a armadura do modelo Steve mantêm seus encaixes.

## Evidências

Os GameTests cobrem a lista de espécies e exclusões, a aquisição do inimigo a 5, 20, 35, 45 e 50 blocos, os limiares de proteção de residente e residência e um percurso real de Seguir e Guarnecer com obstáculo. A suíte gráfica verifica o nome de profissão do Soldado e do Fazendeiro, a recuperação após a cura e o Soldado deitado com armadura completa. Reabrir o mundo em outra JVM verifica os vínculos, os equipamentos, o inventário, a progressão e as demais rotinas persistentes.
