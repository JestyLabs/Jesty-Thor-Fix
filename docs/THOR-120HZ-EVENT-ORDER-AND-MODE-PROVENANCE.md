# Thor mixed-refresh: ordem de eventos e proveniência do modo

Atualizado: 2026-10-08; análise offline de binários e capturas existentes.
O [relatório exato](THOR-120HZ-EXACT-BINARY-OFFLINE-20261008.md) contém hashes,
offsets, disassembly, timelines e classificação PROVEN/OBSERVED/INFERRED/HYPOTHESIS.

## Ordem demonstrada no ELF Thor

```text
desired do display ativo
  -> initiateModeChange(ativo, desired)
  -> initiateModeChange(secundário de altura 1240, MESMO desired)
       -> physicalDisplayId mismatch -> log invalid mode
       -> contador++ / upcoming foreign / marcador FPS
       -> HWC config = (~incomingHwcId) & 1
  -> testa apenas retorno da primeira chamada
  -> conclusão atualiza apenas default
```

**PROVEN**: chamadas em SF `0x1b0fcc/0x1b0fec`, teste primeiro retorno
`0x1b0ff0`; comparação de identidade `0x124248`; log `0x124368`; contador
mismatch `0x1243f4`; remap `0x12448c/0x124490`; HWC `0x1244a4`;
conclusão default `0x1af980/0x1af9c8`.

A revisão AOSP anterior concluía **condicionalmente** que invalid e alvo HWC
tinham de vir de invocações distintas. O ELF exacto contradiz essa condição.
Um contador 1 no BOTTOM é compatível com a própria iniciação mismatch.
Não manter a procura de uma tentativa adicional como requisito para explicar a trace.

## Proveniência e identidades

O objeto do ativo é reutilizado diretamente no secundário; esta é uma origem
concreta do foreign mode. `setDesiredActiveMode` continua a rejeitar fatalmente
identidade errada, mas a segunda iniciação não passa pelo setter do secundário.
O lookup local de um ID no loop não substitui automaticamente o objeto desired
passado para a segunda chamada. Não foi demonstrado stale pointer/alocação
corrompida; não é uma hipótese necessária para os erros observados.

TOP SF 1=120 é invertido para BOTTOM HWC0=120. TOP SF 0=60 é invertido para
BOTTOM HWC1=60. O ID numérico sem identidade/namespace é insuficiente.
O analisador conserva Android Display.Mode.id separado de SF DisplayModeId,
mantém HWC config desconhecido quando a trace não o contém e não atribui
automaticamente scopes anónimas ao display mais próximo no tempo.

## Alinhamento da captura

O anchor `realtime_ts` da linha 16 de atrace permite uma conversão explícita para
UTC; o `parent_ts` da linha 15 não é usado para esse fim. O logcat necessita de
ano 2026 e offset +01:00 explícitos. Só há um anchor com resolução de ms: não há
prova independente de drift nem precisão causal de microssegundos.
O invalid inferior e alvo FPS120 caem no mesmo ms; isso é coerente com o ELF,
sem constituir por si só prova de identidade de invocação runtime.

## Estado por fronteira

`SF_REQUESTED -> SF_VALIDATED -> HWC_REQUESTED -> HWC_ACCEPTED -> SDM_APPLIED -> DRM_ACTIVE -> PHYSICAL_CADENCE`

No secundário o guard de identidade **falha mas continua**; não descrever como
SF_VALIDATED estrito. O request config remapeado pode ser válido na tabela HWC.
Retornos, aplicação por painel e cadência física não estão capturados. Os scopes
Qualcomm process/Submit e o vsync global não preenchem essas lacunas.

## Referência histórica

A [revisão AOSP 21c49252](https://android.googlesource.com/platform/frameworks/native/+/21c49252bf1039465ca5654fcdd3335f40c7ac49/services/surfaceflinger/DisplayDevice.cpp#220)
era a origem da ordem guard->BAD_VALUE->contador discutida em 2026-10-07.
Continua uma referência externa de comparação, não execução Thor. O novo
diagnóstico foi obtido exclusivamente do ELF Build ID
`a4e0851419d45662b0fd5cd067b585bf`; não houve consulta de rede nesta sessão.
