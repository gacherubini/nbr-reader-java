# NBR Reader

API Spring Boot para ler planilha QIII (NBR 12721), extrair valores principais e gerar PDF com resumo de custos.

## Endpoints

- `GET /health`
- `POST /upload/xlsx` -> retorna PDF com tabela:
  1. Terreno (input do usuario)
  2. Projetos (`10 - 7`)
  3. Obra civil (`5`)
  4. Fora obra padrao (`7 - 5`)

## Parametros do endpoint PDF

- `file`: arquivo `.xlsx`
- `cub`: CUB informado pelo usuario
- `terreno`: valor do terreno informado pelo usuario

## Exemplo (PowerShell)

```powershell
curl.exe -X POST "http://localhost:8080/upload/xlsx" `
  -F "file=@C:\caminho\arquivo.xlsx" `
  -F "cub=3500,00" `
  -F "terreno=1200000,00" `
  --output resumo-custos-qiii.pdf
```

## Executar

```powershell
cd C:\Users\Administrator\Documents\codigos\nbr-reader
.\mvnw.cmd spring-boot:run
```

## Testes

```powershell
cd C:\Users\Administrator\Documents\codigos\nbr-reader
.\mvnw.cmd test
```

> Observacao: para rodar Maven Wrapper, configure `JAVA_HOME` para seu JDK.
