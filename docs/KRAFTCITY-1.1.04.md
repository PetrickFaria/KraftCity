# KraftCity 1.1.04

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base 1.1.03, commit `c3f35dd` da branch `feature/kraftcity-builder`.

- O descarte industrial e comercial seleciona automaticamente destinos carregados dentro do alcance já definido. A prioridade é a menor distância horizontal do centro do recipiente ao perímetro da área. Em empate, tem prioridade o destino mais à esquerda ao olhar na direção frontal do posto. A orientação padrão é norte. As coordenadas X, Y e Z resolvem empates restantes.
- Um destino pode receber parte da carga. O posto conserva o restante e tenta os próximos candidatos. A transferência verifica bloqueios vanilla, permissões Forge e filtros do inventário. Recipientes de mods precisam expor `ITEM_HANDLER` para inserção. Baús residenciais de esmeraldas mantêm seu uso específico.
- A reserva agrícola continua sendo de dez unidades por item plantável, incluindo quando existem vários recipientes. As antigas referências manuais de descarte são ignoradas. Vínculos para finalidades independentes, como a logística residencial, continuam disponíveis.
- O botão **Demitir** está nos menus profissionais ocupados. O servidor valida o menu, o posto, o alcance, as permissões e a geração do vínculo. Esse controle impede que um clique atrasado encerre uma contratação posterior. O estoque continua no posto e a obra pode prosseguir com outros construtores.
- A demissão mantém a entidade, a residência e o histórico profissional. O soldado zumbificado pode ser desvinculado sem alterar a entidade ou impedir uma futura cura. Após a cura, fica disponível para contratação.
- A primeira contratação mantém a tabela existente. A recontratação custa uma esmeralda mesmo em outra profissão. Nível e XP continuam por profissão. A opção administrativa de suspender contratos comerciais continua valendo.
