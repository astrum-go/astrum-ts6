🌐 [English](CHANGELOG.md) · [Português (Brasil)](CHANGELOG.pt-BR.md)

# Histórico de Alterações (Changelog)

Este projeto segue um versionamento pré-lançamento baseado em marcos (*milestones*) enquanto o protocolo principal, o áudio e os recursos de transmissão WebRTC estão sendo consolidados.

## [0.9.0-m8] - 2026-08-10

### Adicionado

- Linhas de canais expansíveis com lista direta de participantes.
- Expansão com toque único e entrada no canal com toque duplo.
- Janela de confirmação com solicitação de senha para canais protegidos.
- Suporte a chamadas de vídeo e compartilhamento de tela WebRTC para TeamSpeak 6.
- Presets de qualidade de transmissão (720p/1080p/60fps/480p) e captura de áudio interno do sistema (Android 10+).
- Rotação física automática da câmera para visualização em pé e proporção dinâmica retrato/paisagem.
- Licença pública do repositório, avisos, políticas de privacidade e segurança, diretrizes de contribuição e CI.

### Modificado

- O canal atual expande automaticamente após conexão ou troca de canal.
- As linhas de participantes expõem status de fala, microfone silenciado, som silenciado e identificação do próprio usuário.

## [0.8.0-m7] - 2026-08-09

### Adicionado

- Silenciamento individual e ganho de reprodução de 0% a 200% salvos pela identidade estável do TeamSpeak.

## Marcos Anteriores

Os marcos M0 a M6 estabeleceram a conectividade do protocolo ts3j, ciclo de vida do serviço em primeiro plano, áudio bidirecional Opus, cancelamento de ruído com RNNoise e DeepFilterNet, modos de microfone, roteamento de áudio, reconexão automática e troca de canais.
