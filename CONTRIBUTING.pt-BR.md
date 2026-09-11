🌐 [English](CONTRIBUTING.md) · [Português (Brasil)](CONTRIBUTING.pt-BR.md)

# Contribuindo

Obrigado pelo interesse em contribuir com o TS6 Mobile Community. Como este é um cliente de código aberto, as alterações devem preservar a estabilidade da conexão, a latência de áudio controlada e o pleno controle do usuário sobre a transmissão do microfone.

## Antes de abrir uma alteração

- Pesquise em *issues* e *pull requests* existentes.
- Abra uma *issue* para discutir alterações de comportamento que necessitem de alinhamento prévio.
- Nunca inclua senhas de servidores, arquivos de identidade, endereços de servidores reais, dados de participantes, caminhos locais de SDK, APKs, keystores ou chaves de assinatura.
- Mantenha alterações focadas e siga os estilos existentes de Kotlin e Jetpack Compose.
- Novos comportamentos de áudio e protocolo devem incluir testes unitários direcionados sempre que viável.

## Verificações locais

Utilize o JDK 17 e as versões de Android/NDK listadas no README. Antes de submeter um pull request, execute:

```powershell
.\gradlew.bat :ts6-protocol:test :audio-opus:testDebugUnitTest :audio-opus:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Alterações no áudio nativo ou na interação de hardware devem ser testadas em um dispositivo físico real. Descreva o dispositivo testado, a versão do Android, a rota de áudio utilizada e o resultado observado sem expor dados privados de servidores.

## Pull Requests

Explique claramente o comportamento visível ao usuário, a abordagem técnica adotada, os testes realizados e quaisquer limitações conhecidas.

Todos os colaboradores devem seguir o [Código de Conduta](CODE_OF_CONDUCT.pt-BR.md).
