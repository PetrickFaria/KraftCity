# KraftCity 1.1.14

Base: KraftCity 1.1.13, commit `c62a81499c0aa82f46f3117cce809c201e1bd340`. Requer Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Ferramentas e sprites

A espada, o machado e a enxada de esmeralda usavam `item/generated`, que não possui as transformações de mão das ferramentas. Cada modelo agora herda o modelo da ferramenta de ferro correspondente e fornece a própria textura. A primeira pessoa, a terceira pessoa e as camadas personalizadas de mãos e costas dos trabalhadores passam pelas transformações da ferramenta de ferro. As animações e os atributos dos itens mantêm a configuração anterior.

O cliente Forge usa as texturas instaladas como fonte dos sprites de 16×16. As ferramentas de esmeralda mantêm a forma e a cor anterior da lâmina e usam nos cabos os pixels correspondentes da ferramenta de ferro. Nas varas de pesca, inclusive quando lançadas, a cor muda só na estrutura. O anzol, a linha e o canal alfa vêm da imagem instalada. As tesouras recolorem apenas as regiões metálicas claras. A vara de madeira e a tesoura de ferro vanilla continuam a usar os recursos originais do Minecraft.

## Área do Fazendeiro

O posto registra o UUID do trabalhador que selecionou a área. A contratação de outro aldeão começa em 5×5 e reinicia os cursores de varredura, mesmo quando o contratado traz XP legítimo de um emprego anterior. O mesmo agricultor mantém a área quando o vínculo continua válido, inclusive por descarregamento, cura ou recuperação legítima do bloco. A progressão só desbloqueia tamanhos maiores. Postos antigos ocupados preservam a área e registram seu titular quando o vínculo é liberado ou o bloco é recolhido.

## Zumbificação e cura

O servidor registra o UUID do aldeão zumbi e o associa ao UUID original em `EmploymentData`. O UUID original continua identificado no posto, na residência e no registro de progresso. Assim, uma conversão confirmada não executa a limpeza de uma morte definitiva. Ao curar, o servidor restaura a identidade antes de inserir a entidade no mundo. A associação é consumida depois que a entidade é adicionada com sucesso. Apenas a conversão associada ao registro pode recuperar essa identidade enquanto o trabalhador estiver infectado.

O posto e os inventários continuam sob a autoridade do servidor. Se o posto foi destruído ou outra pessoa assumiu a vaga, o aldeão curado mantém o histórico e a residência válida e pode ser contratado em um posto livre. A perda definitiva do zumbi remove a reserva e libera os vínculos. O Soldado mantém seu fluxo próprio de transformação, cura e equipamento.

## Evidências de validação

A suíte de GameTests cobre a conversão vanilla das profissões civis, a cura própria do Soldado, a recuperação de área e XP, o descarregamento do zumbi, tentativas de usar a identidade reservada, a perda definitiva do zumbi e a contratação de substitutos. Os testes de colheita preservam o cenário de culturas vizinhas. O cliente Minecraft compara os sprites e as transformações das ferramentas e captura as mãos, a animação e as costas do trabalhador. Uma segunda JVM reabre o mundo com um Fazendeiro zumbificado e confere o posto, a residência, 13×13 e o mesmo nível e XP após a cura.

A execução do GitHub Actions e seu artefato registram os resultados, as capturas, os logs e o SHA-256.
