# KraftCity 1.1.30

Base 1.1.29, commit 16ddce289f5c8a488a58bc56e05b05a302f39bbc. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Passeio industrial

A rotina profissional informa quando existe trabalho. Só então o agendador sabe se pode iniciar uma caminhada a 0,55 da velocidade de navegação, com pausas aleatórias de 35 a 100 ticks. O caminho completo precisa ficar na área, em células transitáveis, com suporte seguro e sem passar por terra arada, cultivos ou áreas ocupadas por outro trabalhador. A interrupção compara o caminho atual com o caminho ocioso para preservar novas rotas profissionais e residenciais. Soldados permanecem no sistema militar.

## Pólen e mel

O Minecraft credita o néctar ao liberar a abelha pelo caminho vanilla. O mínimo vanilla de 2 400 ticks é reduzido para 100 ticks configuráveis em `apiarioDepositoTicks` apenas quando o ocupante traz néctar e possui um vínculo válido. O tempo economizado é descontado antes da próxima polinização, enquanto a abelha está disponível para voar. O prazo usa o relógio persistente do mundo. O cálculo desconta o tempo efetivamente gasto na colmeia para lidar com saídas atrasadas. Noite, chuva, emergência, saída obstruída e o incremento de mel continuam no código vanilla.

Os testes separam a primeira entrega do intervalo entre entregas subsequentes. A primeira fica adiantada pela saída mais rápida. A comparação do ritmo usa os ciclos seguintes em um par de apiários com condições controladas.

## Vínculo individual

O posto fornece capacidade, área e inventário. Cada registro de domesticação identifica também o UUID do Apicultor contratado. A morte definitiva e a demissão encerram a colônia, liberam a capacidade e retiram das abelhas as marcas domésticas. As abelhas gravadas seguem na colmeia e são reconciliadas quando o bloco volta a executar seus ticks ou quando saem. A conversão recuperável usa o UUID que a cura vanilla restitui.

Para registros antigos, a migração só atribui a colônia quando o posto ainda registra o primeiro contrato com o mesmo trabalhador. Um histórico com substituições deixa os vínculos anteriores sem dono verificável. Nesse caso, os sobreviventes voltam a ser selvagens e podem ser domesticados novamente. O comando de recuperação da 1.1.29 continua disponível para cadastros ausentes confirmados.

## Colmeias excedentes

O Apicultor conserva as colmeias ativas e só recolhe excedentes próximos com espaço no estoque. A coleta registra o tipo, o nível de mel e todos os dados do bloco, inclusive ocupantes, em um item de colmeia reservado. O item fica fora das ofertas de descarte. O trabalhador verifica alcance, permissões Forge e o estado do bloco antes de recolher.

Depois de subir de nível, o Apicultor pode usar um item guardado sobre uma cerca sem conexões horizontais, dentro da seleção ativa. O destino deve estar livre e ter uma saída desobstruída para as abelhas. A colocação passa pela operação de item do Forge e devolve o item ao estoque se for recusada. A seleção da área permanece manual.

## Testes

Os GameTests exercitam passeio e retomada da colheita, morte e substituição no mesmo posto, zumbificação e cura do Apicultor, tempo de visita comparado a uma abelha selvagem e transporte de ninho ocupado com mel, incluindo cerca conectada e isolada. A suíte padrão verifica o cliente e a reabertura em outra JVM.
