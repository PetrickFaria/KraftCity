# KraftCity 1.1.03

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base 1.1.02, commit `81a3194` da branch `feature/kraftcity-builder`.

- O percurso começa no noroeste, avança para leste e alterna a direção a cada fileira ao sul. Postos antigos descartam a orientação anterior e reiniciam o percurso.
- O Fazendeiro continua recolhendo itens antes de colher e precisa chegar fisicamente a cada alvo. Ao plantar cana em solo irrigado, reinicia o percurso normal no noroeste. Na colheita da cana de três blocos, conserva a base e recolhe as duas unidades no mundo.
- A colheita de culturas comuns edita a célula selecionada. Remover o fruto atualiza o caule conectado para um caule maduro. Os testes de caminhada comparam as 25 células e os respectivos solos para trigo, cenoura, batata, beterraba, melancia e abóbora.
- Caules novos ficam no perímetro e precisam de espaço lateral para o fruto. O Minecraft aceita terra arada vazia como solo do fruto. Quando vegetação substituível bloqueia o espaço, o trabalhador pode removê-la e saltar. A conversão ocorre somente ao aterrissar na célula autorizada e vazia. O log de depuração registra rota, colheita e pisoteamento.
- Trabalhadores continuam durante a chuva comum e procuram abrigo em tempestades. A fuga de zumbis pode ultrapassar a área de trabalho. Após um intervalo sem ameaça, o trabalho volta ao horário normal. Soldados conservam a prioridade de combate.
- O descarte conserva dez unidades de cada item plantável no estoque compartilhado e preserva enxadas, farinha de osso e baldes de água. A descoberta inclui recipientes com `ITEM_HANDLER` em chunks carregados, respeitando filtros e acesso pela face. O GameTest usa um recipiente com capability anexada e filtro de entrada para reproduzir a integração Forge.
- O trabalhador abre portões de cerca no caminho mediante as permissões aplicáveis. O portão fecha depois que a passagem estiver livre.
