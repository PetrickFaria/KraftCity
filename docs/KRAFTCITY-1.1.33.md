# KraftCity 1.1.33 — transparência das skins de residentes no mundo

Base 1.1.32 em `71420a78191c1cc5e86ff3cd2f6b24413a4fb409`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

O caso foi reproduzido com a skin original `A-Apicultor.png`, 64×64, SHA-256 `29fe7a2cac6077ec56cd206ab3cf962777eca0f575967dc8d0c88e45d4656adb`. O JAR 1.1.32 mostrou os nomes dos residentes com os corpos invisíveis a 100% no mundo com Oculus, Rubidium e Complementary ativos. Desativar o shaderpack na mesma cena restaurou os corpos.

O shader próprio da skin suporta todo o canal alfa, mas contorna os fornecedores de shader que o Oculus intercepta para compor a cena. Com um shaderpack Iris/Oculus ativo, as peles fracionárias do mundo usam o passe padrão de entidades translúcidas, que o pipeline externo reconhece. A prévia mantém o shader próprio e a descarga imediata de seus buffers. Sem shaderpack, o shader próprio também continua no mundo.

A opacidade geral chega à emissão normal das partes do modelo, incluindo o caminho que o Rubidium otimiza. O emissor individual de cubos continua atendendo os modelos que o chamam diretamente, como o EMF. Um escopo impede a segunda multiplicação quando os dois caminhos se encontram. A prévia mantém sua própria aplicação de opacidade. Os dados da skin e os valores salvos do slider permanecem em seus formatos existentes.
