# KraftCity 1.1.27

Correção de domesticação do Apicultor a partir da 1.1.26, commit 71d1a9ac3f389b1ba96136c572c0806626c69d7c. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Detecção, voo e entrega

O Apicultor descartava uma abelha sem linha de visão antes de oferecer a flor. Mesmo no alcance de dez blocos, a abelha podia ficar sem objetivo de voo até se deslocar por conta própria. A seleção agora inclui a abelha encoberta, prioriza a que já está respondendo à flor e aponta para uma posição interna à área. O voo usa o controle normal da abelha com caminho navegável quando a aproximação direta está obstruída. A entrega exige proximidade, linha de visão e posição da abelha dentro da área. O posto pode iniciar sem colmeia.

O evento de interação com a abelha recebia o conteúdo do slot de tesoura. A interação de domesticação envia a flor apresentada pelo Apicultor ao Forge e respeita cancelamentos de proteção. A regra de modificação do ambiente continua protegendo coleta e reprodução. Oferecer uma flor pode passar pelas verificações específicas de interação sem depender da regra global de modificação de blocos por criaturas.

## Diagnóstico e testes

Ative `-Dkraftcity.debugApiaryTaming=true` na JVM de desenvolvimento para registrar mudanças de estado, candidatos, flores e capacidade, além da ativação da atração e de cada entrega com o progresso do vínculo.

Os testes usam a rotina agendada do servidor, com papoula sem colmeia, dente-de-leão com colmeia e centáurea a curta distância. A verificação Forge confere o item reportado em uma interação profissional. Os demais testes continuam cobrindo alcance de dez blocos, rejeição a onze, limite populacional, reprodução, coleta, circulação e persistência.
