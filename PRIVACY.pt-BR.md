🌐 [English](PRIVACY.md) · [Português (Brasil)](PRIVACY.pt-BR.md)

# Política de Privacidade

Última atualização: 10 de agosto de 2026

O TS6 Mobile Community é um cliente não oficial que conecta diretamente do dispositivo Android aos servidores TeamSpeak escolhidos pelo usuário. Os mantenedores do projeto não operam nenhum serviço intermediário para essas conexões.

## Dados processados no dispositivo

- Endereço do servidor, porta, apelido (nickname) e senha do servidor permanecem na memória do aplicativo/serviço durante a sessão ativa de conexão. O projeto não os persiste deliberadamente no armazenamento do aparelho.
- A identidade gerada do TeamSpeak é criptografada localmente com AES-GCM usando uma chave protegida pelo Android Keystore.
- Senhas de servidores e de canais são usadas estritamente para a conexão solicitada. Uma senha de canal necessária para reconexão é retida apenas na memória do serviço em primeiro plano e descartada quando o processo é encerrado.
- O áudio do microfone é capturado apenas enquanto o modo de microfone selecionado estiver transmitindo ativamente. Ele é codificado e transmitido diretamente ao servidor conectado.
- O tráfego de voz recebido é decodificado e reproduzido localmente. O aplicativo não grava nem armazena conversas de voz.
- Preferências individuais de silenciamento e ganho de volume são armazenadas localmente, indexadas pela identidade estável do participante no TeamSpeak.

## Dados NÃO coletados por este projeto

O aplicativo não contém analytics, publicidade, telemetria, relatórios de falhas operados pelo projeto ou servidores de backend próprios. Portanto, os mantenedores não recebem detalhes de conexões, identidades, áudio ou eventos de uso do aplicativo.

O administrador do servidor TeamSpeak, seu provedor de internet/rede, a plataforma Android e o fabricante do dispositivo podem processar dados sob seus próprios termos. Consulte as políticas do servidor que você utiliza.

## Permissões

O aplicativo solicita permissões de rede para se conectar aos servidores, acesso ao microfone para transmissão de voz, Bluetooth/áudio para seleção de rota de saída, acesso a notificações para o serviço em primeiro plano e permissão de projeção de mídia para compartilhamento de tela. A negação de permissões opcionais pode desativar o recurso correspondente.

## Exclusão de Dados

A desinstalação do aplicativo remove suas preferências locais e a identidade criptografada, de acordo com o comportamento padrão do Android. Limpar os dados do aplicativo nas configurações do sistema tem o mesmo efeito. Dados já recebidos por um servidor TeamSpeak ou por outros participantes estão fora do controle do projeto.

Dúvidas ou preocupações de privacidade não confidenciais podem ser abertas como *issues* no repositório. Para relatórios sensíveis, utilize o processo descrito em [SECURITY.pt-BR.md](SECURITY.pt-BR.md).
