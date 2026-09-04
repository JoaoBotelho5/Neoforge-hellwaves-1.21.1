# Hellwaves

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-blue)
![License](https://img.shields.io/badge/status-active%20development-brightgreen)

Hellwaves é um mod de wave-survival para Minecraft (NeoForge 1.21.1), construído em torno de **Activator Blocks** — estruturas tipo portal que invocam vagas escalonadas de inimigos — e **Guardians**, aliados mortos-vivos domesticáveis com equipamento, upgrades e um sistema de armazenamento persistente.

Este documento foca-se na **arquitetura do código**: como os sistemas estão organizados, que padrões de engenharia foram aplicados e porquê.

## Índice

- [Features](#features)
- [Arquitetura do projeto](#arquitetura-do-projeto)
- [Destaques técnicos](#destaques-técnicos)
- [Fluxo de uma wave](#fluxo-de-uma-wave-passo-a-passo)
- [Instalação](#instalação)

## Features

### Wave Activators
- **Standard Activator** — 3 waves, de zombies/skeletons até um boss Undead Lord.
- **Elite Activator** — 5 waves, culminando num encontro com um Warden, raio de spawn maior e melhores recompensas.
- Equipamento por wave (armas, armaduras, efeitos) é **data-driven via JSON**, com seleção por peso — balanceamento sem recompilar.
- O bloco cresce visualmente conforme as waves avançam; se um inimigo alcançar o ativador, este detona.

### Guardians
- GUI de inventário dedicada (armadura, mãos, storage extra), sincronizada em tempo real com vida, armadura, toughness e dano de ataque.
- **Soul Cages** — captura um guardian preservando nível, vida, equipamento e nome customizado; liberta-o noutro sítio.
- Proteção de fogo amigo com Iron Golems, Snow Golems e Wolves, exceto em legítima defesa.
- Upgrades disparados via packets de rede customizados.

### Warped Miner
Mob hostil que faz pathfinding até um bloco-alvo e mina preditivamente os obstáculos no caminho (respeitando uma lista de blocos protegidos), com uma regra "3 hits" que o muda para combate direto assim que é atacado o suficiente.

### Equipamento custom
- **Greatsword** — arma pesada de duas mãos com tier de ferramenta netherite custom.
- **Guardian Summoners** para invocar Zombie/Skeleton Guardians diretamente.

## Arquitetura do projeto

O código está organizado por **responsabilidade de domínio**, não por tipo técnico — cada package resolve um problema específico do mod:

```
com.hellwaves.hellwavesmod
├── HellwavesMod.java            → entrypoint (@Mod), liga todos os subsistemas no boot
├── HWClientSetup.java           → registo client-side (renderers, screens)
│
├── Blocks/                      → Activator Blocks (bloco + block entity + registo)
│   ├── ActivatorBlock.java / ActivatorBlockEntity.java
│   ├── EliteActivatorBlock.java / EliteActivatorBlockEntity.java
│   └── ModBlockEntities.java
│
├── HWMobs/                      → entidades custom e a sua lógica de IA
│   ├── WarpedMiner.java / WarpedMinerBreakGoal.java
│   ├── GlobalMiningReservation.java
│   └── IGuardian.java           → contrato partilhado por todos os guardians
│
├── Waves/                       → lógica de spawn de uma wave
│   ├── Wave.java                → waves standard
│   └── EliteWave.java           → waves elite (spawn radial, casos especiais p/ Warden/Phantom)
│
├── WavesManager/                → definição estática das waves + config JSON
│   ├── WaveManager.java
│   └── EliteWaveManager.java
│
├── equipment/                   → sistema de loadouts data-driven
│   ├── EquipmentConfig.java / EquipmentHelper.java / EliteEquipmentHelper.java
│
├── Items/                       → itens custom (armas, summoners, soul cages)
│
├── inventory/                   → GUI dos Guardians
│   ├── GuardianInventory.java       → implementação de Container
│   ├── GuardianInventoryMenu.java   → AbstractContainerMenu + sync de stats
│   └── ModMenuTypes.java
│
├── packets/                     → networking client → server
│   ├── Modpackets.java
│   └── upgradeguardianpacket.java
│
├── regivents/                   → registo central + event listeners
│   ├── HWDeferredRegister.java  → single source of truth para blocks/items/entities
│   ├── HWEvents.java / HWCreativeEvents.java
│   └── ZombieGuardianEvents.java / SkeletonGuardianEvents.java
│
├── targeting/                   → GuardianTargeting.java (injeção dinâmica de IA)
│
└── client/                      → renderers das entidades custom
```

## Destaques técnicos

### 1. Registo centralizado com `DeferredRegister`
Todo o registo de `Block`, `Item`, `EntityType` e `BlockEntityType` passa por `HWDeferredRegister`/`ModBlockEntities`, seguindo o padrão de registo adiado do NeoForge. Isto evita problemas de ordem de carregamento de classes e mantém uma única fonte de verdade para todo o conteúdo do mod.

### 2. Arquitetura orientada a eventos
Em vez de alterar classes core do jogo, o comportamento é injetado via `@SubscribeEvent` no barramento de eventos:
- `ZombieGuardianEvents` / `SkeletonGuardianEvents` cancelam `LivingChangeTargetEvent` para implementar proteção de fogo amigo com exceção de legítima defesa.
- `GuardianTargeting` escuta `EntityJoinLevelEvent` e injeta dinamicamente um `Goal` customizado em **qualquer** mob hostil que entra no mundo (vanilla ou de outro mod), fazendo-o perseguir Guardians — sem precisar de tocar na classe de cada mob individualmente.

### 3. Sistema data-driven de equipamento
`EquipmentConfig`/`EquipmentHelper`/`EliteEquipmentHelper` carregam tabelas JSON no arranque e escolhem o loadout de cada wave através de um algoritmo de seleção por peso cumulativo (`getRandomItem`). Balancear o jogo (armas, armaduras, drop rates) é uma edição de JSON, zero recompilação.

### 4. Máquina de estados para IA de mob
`WarpedMiner` implementa, à mão, uma pequena máquina de estados dentro de `tick()`:

```
navegar até o alvo → detetar "preso" (30 ticks parado) → minerar preditivamente
    → aplicar regra dos 3 hits → mudar para combate direto
```
`WarpedMinerBreakGoal` isola a sub-lógica de mineração (lista negra de blocos protegidos, tempo de quebra proporcional à dureza do bloco). `GlobalMiningReservation` é um registo de reservas thread-safe (`ConcurrentHashMap`) que impede dois miners de minarem o mesmo bloco em simultâneo.

### 5. Sincronização cliente-servidor
`GuardianInventoryMenu` estende `AbstractContainerMenu` e expõe vida, armadura, toughness e nível em tempo real através de uma implementação custom de `ContainerData`, desacoplando a UI (`Screen`) do estado autoritativo do servidor sem expor internals da entidade.

### 6. Networking custom
`upgradeguardianpacket` implementa `CustomPacketPayload` com `StreamCodec` para serialização type-safe, despachado via `Modpackets`/`PacketDistributor` — permite disparar upgrades de guardian a partir de interações no cliente sem gambiarras de NBT sync.

### 7. Serialização/persistência de entidades vivas
`SoulCageItem`/`EmptySoulCageItem` serializam o NBT completo de uma entidade viva (`LivingEntity#saveWithoutId`) mais metadados custom (nível, nome, tipo) num `DataComponent` do `ItemStack`, e reconstroem uma entidade equivalente na libertação — na prática, um pipeline de serialização/desserialização de objetos de jogo com estado.

### 8. Polimorfismo orientado por interface
`IGuardian` abstrai o comportamento partilhado (nível, inventário, flag de restauro de cage) entre `ZombieGuardian` e `SkeletonGuardian`, permitindo que sistemas transversais (`GuardianInventoryMenu`, `SoulCageItem`, `upgradeguardianpacket`) operem sobre qualquer guardian sem conhecer o tipo concreto.

## Fluxo de uma wave (passo a passo)

1. `ActivatorBlock.tick()` corre no servidor a cada tick agendado e delega a lógica de estado ao `ActivatorBlockEntity`.
2. Quando não há mobs ativos e o countdown chega a zero, `WaveManager`/`EliteWaveManager` resolve a `Wave` correspondente ao número atual.
3. `Wave.spawn()` posiciona os mobs em formação radial à volta do ativador, aplica o `EquipmentHelper` (gear via JSON) e injeta o `WalkCenterGoal` para os forçar a convergir.
4. `ActivatorBlockEntity` mantém a lista de mobs ativos; se um deles entra no raio de ativação, o bloco explode e reinicia o progresso.
5. Ao completar todas as waves, recompensas são geradas (`ItemEntity`) e o bloco remove-se a si próprio.

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
