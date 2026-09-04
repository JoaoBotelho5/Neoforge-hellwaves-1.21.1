# Hellwaves

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-blue)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento%20ativo-brightgreen)

CurseForge Link: https://www.curseforge.com/minecraft/mc-mods/hellwaves

Mod de wave-survival para Minecraft, desenvolvido em Java sobre o framework NeoForge (1.21.1). Para além do conteúdo de jogo, o projeto serve como demonstração prática de conceitos de engenharia de software aplicados num sistema real: concorrência, arquitetura orientada a eventos, design de sistemas data-driven, sincronização cliente-servidor e networking custom.

## O que este projeto demonstra

| Área | Onde está aplicado |
|---|---|
| **Concorrência e estruturas thread-safe** | `GlobalMiningReservation` usa `ConcurrentHashMap.newKeySet()` para coordenar acesso exclusivo a um recurso partilhado entre múltiplas entidades, evitando race conditions sem locks explícitos. |
| **Máquinas de estado escritas à mão** | `WarpedMiner` implementa uma state machine em Java puro (navegação → deteção de bloqueio → mineração preditiva → transição para combate), sem depender de framework externo. |
| **Arquitetura orientada a eventos** | Sistema de listeners (`@SubscribeEvent`) que injeta comportamento — targeting, permissões, colisões — sem alterar as classes que despoletam os eventos. Mesmo princípio de desacoplamento usado em arquiteturas pub/sub. |
| **Serialização de objetos com estado** | `SoulCageItem` implementa uma pipeline de serialização/desserialização (estado + metadados custom), com tratamento explícito de edge cases (modo criativo vs. sobrevivência). |
| **Networking type-safe** | Packets custom com `StreamCodec` para serialização binária estruturada cliente-servidor, com validação de contexto (distância, autoridade do servidor) antes de aplicar mudanças de estado. |
| **Sistemas data-driven** | Configuração de gameplay via JSON carregado em runtime, com algoritmo de seleção por peso cumulativo — separação clara entre lógica e dados, sem necessidade de recompilar para rebalancear. |
| **Interfaces e polimorfismo** | `IGuardian` como contrato partilhado entre implementações distintas, permitindo que sistemas transversais (GUI, packets, persistência) operem de forma genérica sobre qualquer tipo concreto. |
| **Registo centralizado (padrão registry/factory)** | `DeferredRegister` gere a criação e o ciclo de vida de dezenas de objetos de jogo, evitando acoplamento direto e problemas de ordem de inicialização. |

## Sobre o Hellwaves

### Wave Activators
Blocos que trigam sequências de waves de inimigos automaticamente:
- **Standard Activator** — 3 waves, de zombies/skeletons até um boss Undead Lord.
- **Elite Activator** — 5 waves, culminando num encontro com um Warden, raio de spawn maior e melhores recompensas.

O equipamento de cada wave é configurável via JSON (armas, armaduras, efeitos), sem necessidade de recompilar. O bloco cresce visualmente conforme as waves avançam; se um inimigo alcançar o ativador, este detona.

### Guardians
Zombie e Skeleton Guardians são aliados upgradeáveis com:
- GUI de inventário dedicada, sincronizada em tempo real com vida, armadura, toughness e dano de ataque.
- **Soul Cages** — captura um guardian preservando nível, vida, equipamento e nome; liberta-o noutro sítio.
- Proteção de fogo amigo com Iron Golems, Snow Golems e Wolves, exceto em legítima defesa.
- Upgrades disparados via packets de rede customizados.

### Warped Miner
Mob hostil que faz pathfinding até um bloco-alvo e mina preditivamente os obstáculos no caminho (respeitando uma lista de blocos protegidos), com uma regra "3 hits" que o muda para combate direto assim que é atacado o suficiente.

### Equipamento custom
- **Greatsword** — arma pesada de duas mãos com tier de ferramenta netherite custom.
- **Guardian Summoners** para invocar Zombie/Skeleton Guardians diretamente.

## Estrutura do projeto

```
com.hellwaves.hellwavesmod
├── HellwavesMod.java            → entrypoint (@Mod), liga todos os subsistemas no boot
├── HWClientSetup.java           → registo client-side (renderers, screens)
│
├── Blocks/                      → Activator Blocks (bloco + block entity + registo)
├── HWMobs/                      → entidades custom e a sua lógica de IA
├── Waves/                       → lógica de spawn de uma wave (standard e elite)
├── WavesManager/                → definição estática das waves + carregamento do JSON
├── equipment/                   → sistema de loadouts data-driven
├── Items/                       → itens custom (armas, summoners, soul cages)
├── inventory/                   → GUI dos Guardians (Container, Menu, sync de stats)
├── packets/                     → networking client → server
├── regivents/                   → registo central + event listeners
├── targeting/                   → injeção dinâmica de IA (GuardianTargeting)
└── client/                      → renderers das entidades custom
```

## Arquitetura em detalhe

**Registo centralizado com `DeferredRegister`**
Todo o registo de `Block`, `Item`, `EntityType` e `BlockEntityType` passa por `HWDeferredRegister`/`ModBlockEntities`, seguindo o padrão de registo adiado do NeoForge — única fonte de verdade para todo o conteúdo do mod, sem problemas de ordem de carregamento de classes.

**Sistema de eventos**
`GuardianTargeting` escuta `EntityJoinLevelEvent` e injeta dinamicamente um `Goal` customizado em qualquer mob hostil que entra no mundo (vanilla ou de outro mod), fazendo-o perseguir Guardians sem tocar na classe de cada mob individualmente. `ZombieGuardianEvents`/`SkeletonGuardianEvents` cancelam `LivingChangeTargetEvent` para implementar fogo amigo com exceção de legítima defesa.

**Equipamento data-driven**
`EquipmentConfig`/`EquipmentHelper`/`EliteEquipmentHelper` carregam tabelas JSON no arranque e escolhem o loadout de cada wave através de seleção por peso cumulativo (`getRandomItem`).

**IA do Warped Miner**
```
navegar até o alvo → detetar "preso" (30 ticks parado) → minerar preditivamente
    → aplicar regra dos 3 hits → mudar para combate direto
```
`WarpedMinerBreakGoal` isola a sub-lógica de mineração (lista negra de blocos protegidos, tempo de quebra proporcional à dureza do bloco). `GlobalMiningReservation` impede dois miners de minarem o mesmo bloco em simultâneo.

**Sincronização cliente-servidor**
`GuardianInventoryMenu` estende `AbstractContainerMenu` e expõe vida, armadura, toughness e nível em tempo real através de uma implementação custom de `ContainerData`, desacoplando a UI do estado autoritativo do servidor.

**Networking**
`upgradeguardianpacket` implementa `CustomPacketPayload` com `StreamCodec` para serialização type-safe, despachado via `Modpackets`/`PacketDistributor`.

**Persistência de entidades**
`SoulCageItem`/`EmptySoulCageItem` serializam o NBT completo de uma entidade viva (`LivingEntity#saveWithoutId`) mais metadados custom (nível, nome, tipo) num `DataComponent` do `ItemStack`, e reconstroem uma entidade equivalente na libertação.

## Fluxo de uma wave (passo a passo)

1. `ActivatorBlock.tick()` corre no servidor a cada tick agendado e delega a lógica de estado ao `ActivatorBlockEntity`.
2. Quando não há mobs ativos e o countdown chega a zero, `WaveManager`/`EliteWaveManager` resolve a `Wave` correspondente ao número atual.
3. `Wave.spawn()` posiciona os mobs em formação radial à volta do ativador, aplica o equipamento (JSON) e injeta o `WalkCenterGoal` para os forçar a convergir.
4. `ActivatorBlockEntity` mantém a lista de mobs ativos; se um deles entra no raio de ativação, o bloco explode e reinicia o progresso.
5. Ao completar todas as waves, recompensas são geradas e o bloco remove-se a si próprio.

## Instalação

1. Clonar este repositório.
2. Abrir em IntelliJ IDEA ou Eclipse.
3. Correr `gradlew --refresh-dependencies` se faltarem bibliotecas, ou `gradlew clean` para reiniciar o ambiente de build.

## Mapping names

Este projeto usa as mappings oficiais da Mojang para métodos e campos. Ver os termos de licença em:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

## Recursos

- Docs do NeoForged: https://docs.neoforged.net/
- Discord do NeoForged: https://discord.neoforged.net/
