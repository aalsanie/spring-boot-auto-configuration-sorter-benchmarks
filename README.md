baseline:

```text
ef67de058ed35c0f6246d47abdb09fbdd4ce5c1a
```

commit:

```text
2f1d37f6aedc0823e42f20528872ceec0a7d4101
```

### JMH

| Workload | Baseline | Optimized | Reduction |
| --- | ---: | ---: | ---: |
| `NONE`, 1024 | 10,881.55 µs/op | 276.46 µs/op | 97.46% |
| `MIXED`, 1024 | 26,345.35 µs/op | 436.63 µs/op | 98.34% |
| `FAN_IN`, 1024 | 59,197.58 µs/op | 393.20 µs/op | 99.34% |

`FAN_IN` scaling:

| Candidates | Baseline | Optimized |
| ---: | ---: | ---: |
| 256 | 2,838.82 µs/op | 81.13 µs/op |
| 1024 | 59,197.58 µs/op | 393.20 µs/op |
| 2048 | 264,051.02 µs/op | 903.91 µs/op |

JMH configuration:

```text
3 forks
5 x 1s warmup iterations
8 x 1s measurement iterations
```

### Application startup

1024 ordered auto-configurations, 20 interleaved fresh JVM runs per implementation.

| | Baseline | Optimized |
| --- | ---: | ---: |
| P25 | 1698.92 ms | 1648.76 ms |
| Median | 1712.04 ms | 1661.24 ms |
| P75 | 1731.78 ms | 1669.25 ms |
| Process median | 1900.29 ms | 1846.03 ms |

```text
Median context startup saved: 50.806 ms
Median context startup improvement: 2.97%
```

`jmh`:

```powershell
& "$BootRepo\gradlew.bat" `
    -p . `
    "-PbootRepo=$BootRepo" `
    "-PjmhArgs=AutoConfigurationSorterBenchmark.sort -f 3 -wi 5 -i 8 -w 1s -r 1s -p shape=NONE -p size=1024 -prof gc" `
    jmh
```

```powershell
& "$BootRepo\gradlew.bat" `
    -p . `
    "-PbootRepo=$BootRepo" `
    "-PjmhArgs=AutoConfigurationSorterBenchmark.sort -f 3 -wi 5 -i 8 -w 1s -r 1s -p shape=MIXED -p size=1024 -prof gc" `
    jmh
```

```powershell
& "$BootRepo\gradlew.bat" `
    -p . `
    "-PbootRepo=$BootRepo" `
    "-PjmhArgs=AutoConfigurationSorterBenchmark.sort -f 3 -wi 5 -i 8 -w 1s -r 1s -p shape=FAN_IN -p size=256,1024,2048 -prof gc" `
    jmh
```

`app`:

```powershell
& "$BootRepo\gradlew.bat" `
    -p . `
    "-PbootRepo=$BootRepo" `
    :ordered:classes
```

```powershell
& "$BootRepo\gradlew.bat" `
    -p . `
    "-PbootRepo=$BootRepo" `
    -q `
    :ordered:runInfo
```

```powershell
& "$BootRepo\gradlew.bat" `
    -p . `
    "-PbootRepo=$BootRepo" `
    -q `
    :ordered:runSingle
```