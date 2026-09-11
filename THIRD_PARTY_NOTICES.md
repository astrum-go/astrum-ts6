🌐 [English](THIRD_PARTY_NOTICES.md) · [Português (Brasil)](THIRD_PARTY_NOTICES.pt-BR.md)

# Third-Party Notices

Astrum TS6 incorporates or depends on the following third-party software. Each
component remains subject to its own license.

## ts3-mobile (monet4070)

- Project: [monet4070/ts3-mobile](https://github.com/monet4070/ts3-mobile)
- Original author: monet
- Original license: Apache License 2.0 ([http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0))
- Description: Initial Android TeamSpeak 3 mobile client architecture upon which Astrum TS6 was built and expanded.

## ts3j

- Project: [Manevolent/ts3j](https://github.com/Manevolent/ts3j)
- Pinned revision: `db57d60c989e399626aa16d921390f5033e6cdeb`
- License: Apache License 2.0 ([http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0))

## libopus

- Project: [Xiph.Org Opus](https://opus-codec.org/)
- Package version: 1.3.1
- License: BSD 3-Clause
- License text: [app/src/main/assets/licenses/libopus.txt](app/src/main/assets/licenses/libopus.txt)

## RNNoise

- Project: [Xiph.Org RNNoise](https://gitlab.xiph.org/xiph/rnnoise)
- Model/source release: v0.2, vendored from revision `904a876d`
- License: BSD 3-Clause
- License text: [app/src/main/assets/licenses/rnnoise.txt](app/src/main/assets/licenses/rnnoise.txt)
- Vendored provenance: [audio-opus/src/main/cpp/third_party/rnnoise/VERSION.md](audio-opus/src/main/cpp/third_party/rnnoise/VERSION.md)

## Android and Kotlin libraries

The project also depends on AndroidX, Jetpack Compose, Material Icons, Kotlin,
and kotlinx.coroutines components distributed under the Apache License 2.0.
Exact versions are declared in `gradle/libs.versions.toml` and resolved by
Gradle. Their copyright notices and source distributions are available from
their respective upstream projects.

No TeamSpeak artwork, logos, or proprietary client source code is included in
this repository.
