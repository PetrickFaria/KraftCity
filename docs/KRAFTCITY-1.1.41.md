# KraftCity 1.1.41

## A. Pedidos

- Retomar o plantio e o replantio do Lenhador dentro dos limites aprovados.
- Recolher drops hostis legítimos sem perder as prioridades e alcances de cada modo militar.
- Orientar a entrada de cada nova colmeia pela face frontal do posto.
- Priorizar recursos apícolas e de outras profissões nas áreas sobrepostas.
- Investigar cor dos olhos e texturas dos braços retos nas skins Villager e Steve.
- Comparar os deslocamentos profissionais com a caminhada vanilla.

## B. Alterações

- `LumberjackWork` exclui o próprio posto do anel externo de obstáculos, mantém a folga junto à muda e considera a área de patrulha como jurisdição militar. O posto militar físico ainda conta como área protegida. O plantio mantém as margens, a validação de solo, a proteção de áreas próximas e o intervalo entre árvores.
- `MilitaryDropOrigin` marca a origem hostil mesmo quando o abate tem autoria militar. Drops verificados podem ser recolhidos por outro Soldado elegível. A classificação genérica continua reservada a itens sem origem conhecida.
- `ApiaryHiveStock` usa a propriedade de orientação do posto e só aceita um local com saída livre nessa direção. A colocação usa o item e seus dados existentes.
- `ProfessionalLoot` reconhece colmeias artificiais e ninhos no mesmo predicado usado pelo Apicultor e pelo Coletor.
- `WorkerTextures` remapeia as faces das mangas personalizadas do atlas Villager e preserva seu alfa. `BuilderArmsLayer` usa o passe de transparência das skins nas mangas personalizadas.
- As rotas comuns de coleta, lenha, apiário, comércio e volta ao posto usam o multiplicador de caminhada 0,50 lido no Minecraft 1.19.2. As rotas de fuga e combate conservam seus multiplicadores. A entrega de fechamento e a aproximação aos cultivos conservam o passo original para respeitar a janela de entrega e os movimentos de recuperação e salto do Fazendeiro.

## C. Validação pendente

A lista final deve distinguir os testes automáticos das seis skins originais e da medição física em percursos equivalentes. Duas skins de 64×64 foram recuperadas de uma cópia do mundo Teste. Ainda são necessárias as quatro variantes restantes e a comparação visual com os mods do usuário.

## D. Diagnóstico

O bloqueio do replantio vinha de duas restrições sobrepostas. Uma patrulha ampla transformava toda a área florestal em zona protegida. Na área mínima, o próprio posto podia invalidar o anel de busca. As duas regras agora distinguem os postos físicos dos seus usos específicos.

## E. Testes

Os resultados da execução Forge e as capturas devem acompanhar a entrega.

## F. Entrega

Versão candidata 1.1.41, na branch `fix/professional-field-regressions`.

## G. Acompanhamento

| Requisito | Status inicial | Evidência |
| --- | --- | --- |
| Replantio | Parcial | Cenário de caminhada com muda na área mínima e verificação de obstáculos. |
| Coleta em Patrulhar | Parcial | Caso de equipamento legítimo com dano preservado e origem ambiental. |
| Alcance em Guarnecer | Implementação preservada | Cenários militares existentes. |
| Orientação das colmeias | Parcial | Casos dos quatro pontos cardeais e do ninho com abelhas. |
| Prioridade apícola | Parcial | Colmeia na área sobreposta e em área exclusiva do Coletor. |
| Cor dos olhos | Parcial | Brancos e pupilas passam a usar os pixels originais no adaptador Fresh Animations 1.8. |
| Braços retos | Parcial | Remapeamento de UV Villager com alfa. |
| Auditoria de velocidade | Parcial | Multiplicadores comparados com o vanilla. |
| Velocidade das rotas comuns | Parcial | Multiplicadores normalizados. |
| Persistência | Não validado | Depende da reabertura no Forge. |
