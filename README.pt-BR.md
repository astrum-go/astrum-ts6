🌐 [English](README.md) · [Português (Brasil)](README.pt-BR.md)

# Astrum TS6

O **Astrum TS6** (Astrum - Client for TS6) é um cliente Android de código aberto para TeamSpeak, construído sobre o protocolo de cliente completo fornecido pelo [Manevolent/ts3j](https://github.com/Manevolent/ts3j), com suporte a chamadas de voz e transmissões de vídeo e tela WebRTC compatíveis com o TeamSpeak 6.

Este é um projeto comunitário não oficial. Não é afiliado, endossado ou patrocinado pela TeamSpeak Systems GmbH. TeamSpeak e nomes/marcas relacionados são de propriedade de seus respectivos titulares.

---

## 💡 Motivação do Projeto

Este projeto foi desenvolvido em decorrência do bloqueio e das instabilidades de acesso ao Discord no Brasil, que forçaram a busca por alternativas independentes, privadas e robustas para comunicação em tempo real. O objetivo principal foi viabilizar uma forma legítima e eficiente de compartilhar tela e vídeo pelo celular — seja para assistir a conteúdos juntos ou para transmitir partidas de jogos com minha namorada —, aproveitando a infraestrutura descentralizada e estável do TeamSpeak 6.

---

## 🏛️ Origem e Créditos (Base do Projeto)

Este projeto foi construído e expandido a partir da base pioneira desenvolvida por [monet4070/ts3-mobile](https://github.com/monet4070/ts3-mobile), cujo trabalho estabeleceu os alicerces fundamentais da arquitetura Android e a integração inicial de cliente TeamSpeak 3 via [Manevolent/ts3j](https://github.com/Manevolent/ts3j).

### ✨ O Que Adicionamos e Evoluímos Nesta Versão Comunitária:
- **Transmissão de Vídeo e Câmera WebRTC para TeamSpeak 6**: Implementação da camada WebRTC completa compatível com a sinalização do TS6, permitindo transmitir a câmera do celular (com rotação e orientação dinâmica física corretas) e assistir às transmissões dos outros participantes em tempo real com modo tela cheia e zoom por pinça.
- **Compartilhamento de Tela Mobile com Áudio do Sistema**: Transmissão da tela do Android via `MediaProjection`, com suporte a captura e mixagem de som interno do celular (músicas e jogos no Android 10+) e presets customizáveis de resolução, FPS e bitrate.
- **Pipeline Neural de Supressão de Ruído (Astrum Clarity)**: Integração de supressor de transientes, supressão por inteligência artificial com DeepFilterNet e RNNoise v0.2, expansor VoiceGate e controles de ganho individual por usuário.
- **Reestruturação Visual Completa**: Redesenho completo da interface em Jetpack Compose com tema dark moderno, gerenciador de servidores salvos com conexão rápida, novo ícone oficial da Astrum e experiência de usuário fluida.

---

## 🚀 Funcionalidades Atuais

- **Identidade TeamSpeak**: Geração de identidade e armazenamento criptografado em AES-GCM via Android Keystore.
- **Conexão em Primeiro Plano (Foreground Service)**: Notificação persistente com ações de controle rápido e desconexão.
- **Árvore Hierárquica de Canais**:
  - Visualização expansível de canais e lista direta de membros conectados.
  - Toque único para expandir e toque duplo para entrar no canal (com suporte a senha).
- **Áudio de Alta Qualidade (Opus + RNNoise + DeepFilterNet)**:
  - Suporte a `OPUS_VOICE` e `OPUS_MUSIC` através da biblioteca nativa `libopus`.
  - Jitter buffer por usuário, ocultação de perda de pacotes (PLC) e mixagem PCM.
  - Modos de microfone: Sempre Desativado, Pressionar para Falar (PTT) e Contínuo.
  - Supressão de ruído avançada por IA com RNNoise v0.2 e DeepFilterNet.
  - Roteamento de áudio dinâmico (auricular, alto-falante, fone com fio, Bluetooth SCO e USB).
  - Ganho individual por usuário (0% a 200%) e silenciamento individual salvo por identidade única.
- **Transmissão de Vídeo e Tela (WebRTC para TeamSpeak 6)**:
  - **Compartilhamento de Câmera**: Orientação física corrigida (vídeo em pé tanto no celular quanto no PC) e alternância dinâmica entre modo retrato `[  | |  ]` e paisagem `[    ]`.
  - **Compartilhamento de Tela**: Transmissão da tela do celular via MediaProjection com encoders VP8/H.264 otimizados para texto e fluidez.
  - **Áudio Interno do Sistema (Android 10+)**: Compartilhamento de áudio de jogos e mídias mixado diretamente na transmissão.
  - **Presets de Transmissão**: 720p @ 30fps, 1080p @ 30fps, 720p @ 60fps e 480p @ 30fps com controle de bitrate.
  - **Visualização de Transmissões**: Assista às transmissões de câmera e tela dos membros do canal em tempo real, com modo tela cheia e zoom por pinça.

---

## 🔒 Privacidade

O projeto não inclui analytics, anúncios, telemetria, SDKs de rastreamento de falhas ou servidores intermediários gerenciados pelo projeto. O aplicativo conecta-se diretamente aos servidores escolhidos pelo usuário. A identidade TeamSpeak é criptografada localmente via Android Keystore.

Consulte o documento completo em [PRIVACY.pt-BR.md](PRIVACY.pt-BR.md).

---

## 📁 Estrutura de Módulos

- `app`: Interface Jetpack Compose, ciclo de vida Android, serviço em primeiro plano e integração WebRTC.
- `ts6-protocol`: Facade JVM do protocolo ts3j, modelos de domínio, sessões e ordenação da árvore de canais.
- `audio-opus`: Codec nativo libopus via JNI, captura, cancelamento de ruído por IA (RNNoise/DeepFilterNet), jitter buffer e roteamento de áudio Android.

---

## 🛠️ Compilação e Desenvolvimento

Consulte o guia de contribuição em [CONTRIBUTING.pt-BR.md](CONTRIBUTING.pt-BR.md) para instruções detalhadas de compilação com JDK 17 e Android SDK/NDK.

### Backend UniFFI experimental

O backend UniFFI do Astrum Core é opt-in e cobre apenas sessão e voz Opus nesta
fase. O caminho padrão continua sendo ts3j/JNI; servidores com senha também
continuam no ts3j. Para gerar o APK experimental:

```bash
./gradlew :app:assembleDebug :app:inspectAstrumCoreApk \
  -PastrumCoreDir=/caminho/para/astrum-core \
  -PastrumCoreRuntime=true \
  -PastrumCoreBackend=uniffi
```

Canais, presença e streams ainda não estão disponíveis nesse backend e falham
explicitamente, em vez de serem tratados como operações bem-sucedidas. O core
revisado para esta fatia é `abfd54d`.
