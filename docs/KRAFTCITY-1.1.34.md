# KraftCity 1.1.34 — corte de árvores naturais

No Forge 43.5.0, um carvalho cresceu por random ticks na área 5×5×5 de um Lenhador contratado e equipado. A base encostava no posto. Na 1.1.33, o posto contava como bloco sólido de uma construção e a busca descartava o carvalho. Em outra reprodução, a copa baixa bloqueava o raio do golpe mesmo depois de o carvalho passar pela busca.

- A busca continua periódica a cada 60 ticks, inclusive durante o passeio. O detector identifica bases no interior da área e registra os troncos conectados de baixo para cima. As folhas confirmam a copa e o detector não segue folhas para procurar outros troncos.
- Somente a face da base que toca o próprio posto é dispensada da verificação de blocos sólidos adjacentes. Troncos colocados por entidades, raízes fora da área e os demais indícios de construção continuam bloqueando a seleção.
- No corte, o alcance horizontal é medido até a face do tronco. O golpe pode passar por folhas naturais e alcançar partes expostas acima e abaixo do centro da face. Folhas persistentes e blocos sólidos continuam obstruindo. O caminho físico permanece dentro da área após entrar nela e usa as colisões do mundo.
- O Lenhador abandona o passeio ao assumir a rota de corte. O trabalho usa a lista de troncos registrada ao selecionar a árvore, o que permite continuar acima da área vertical. Preserva os tempos dos sete machados, a durabilidade, os drops e as verificações de proteção.

Ative `-Dkraftcity.lumberjackDebug=true` na JVM do servidor para registrar expediente, ferramenta e área a cada busca e motivos de rejeição, entrada em rota e início do temporizador. Os registros de estado aparecem quando a atividade muda.
