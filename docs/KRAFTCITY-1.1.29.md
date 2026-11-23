# KraftCity 1.1.29

Base 1.1.28, commit da91a3b336f617c23709bf89088c9dd83cc6ae9e. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Falha no mundo Teste

O level.dat registra KraftCity 1.1.28. O posto de Apicultor em -12 -43 -10, no Overworld, estava ativo, em Lv1 e com área 7×7×5. O backup continha 100 papoulas no slot profissional e 100 dentes-de-leão no estoque. A estação tinha três UUIDs em apiary_bees. Nenhum dos três apareceu nos dados salvos de entidades, colmeias ou inventários. O log da execução oficial 1.1.28 registrou population=3 e capacity=3, bloqueando a domesticação antes de selecionar um alvo. A origem histórica da remoção das três entidades permanece indeterminada.

## Recuperação do registro

O comando de operador arquiva somente os UUIDs informados. O posto e os arredores precisam estar carregados. A operação é recusada integralmente se um UUID informado estiver em uma abelha carregada ou numa colmeia nos arredores. O registro arquivado pode retornar se a abelha original reaparecer na dimensão do posto e houver vaga. Os vínculos de outros postos não participam da operação. A inspeção offline é importante para distinguir uma ausência real de uma entidade descarregada ou armazenada por outro mod.

Para o backup Teste, perto do posto no Overworld:

```text
/kraftcity apiario reparar -12 -43 -10 136e7ff4-2a85-4280-96a1-9a8ba4e3d19c b31632ec-ebcb-45b6-92e9-a08868d60a70 bcfad5c6-c6a6-4a93-ab7f-542f2b02ec24
```

O comando deve ser executado uma vez. A instrumentação existente, ativada por `-Dkraftcity.debugApiaryTaming=true`, informa quantidade de candidatas mesmo quando o lugar está cheio. O registro de diagnósticos é opt-in e limita atualizações repetidas.

## Evidência

Em uma cópia do backup, um ejetor vanilla usou um ovo de abelha e identificou a entidade adulta com uma tag de teste. Após arquivar os três vínculos ausentes, o Apicultor selecionou a mesma entidade, consumiu uma papoula do slot profissional e registrou o novo vínculo. Os dados do arquivo original foram preservados.

O mundo lista 183 mods. O ambiente de teste abriu uma cópia com Forge e KraftCity e registrou mapeamentos e datapacks ausentes dos demais mods. A reprodução e a recuperação no backup foram verificadas nessa configuração reduzida. A configuração completa do usuário e o checksum do JAR que gerou originalmente o save dependem dos arquivos da instalação original.
