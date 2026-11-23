# KraftCity 1.1.28

Correção do crash de renderização da 1.1.27. Base b084af7a026f6870920fcdfeda7627d51c70e77d. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

O renderer dos aldeões é compartilhado. A camada de ferramentas Steve era construída com uma referência bruta a ele, declarando que seu modelo sempre seria Steve. Quando o renderer devolvia um VillagerModel, o cast implícito de getParentModel falhava. A restauração da lista temporária de camadas dependia de consultar novamente a aparência no retorno, permitindo que uma chamada aninhada ou uma mudança de aparência deixasse estado incompatível no renderer.

A camada Steve agora recebe o tipo comum e verifica o modelo efetivo antes de usar os braços ou o torso. O renderer instala a camada apenas enquanto renderiza a variante Steve. Cada chamada aninhada recebe o modelo e a lista de camadas apropriados e restaura o estado salvo para a chamada anterior. Aldeões tradicionais continuam usando sua camada de braços e ferramentas. A correção atua no cliente e preserva moradores, aparências e equipamentos gravados.

A suíte gráfica aciona a mesma camada com VillagerModel e SteveVillagerModel e verifica vértices de tesoura, frasco, flor e ferramenta nas costas. Também reproduz uma chamada aninhada de aldeão tradicional durante a renderização Steve e verifica a restauração após cancelamento e troca de aparência. As verificações do cliente e da reabertura cobrem as profissões, armaduras, inventários e vínculos.
