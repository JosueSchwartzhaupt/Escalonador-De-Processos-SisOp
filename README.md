# Trabalho Final de Sistemas Operacionais

## Grupo,
- Aluno 1: CAUÊ CAVALHEIRO SCHILLING 
- Aluno 2: IGOR TERRES DE OLIVEIRA
- Aluno 3: JOÃO MIGUEL VIEIRA DALSOTO
- Aluno 4: JOSUÉ HENRIQUE BECKER SCHWARTZHAUPT
- Aluno 5: LUCAS DOS PASSOS ALMEIDA
- Aluno 6: VICTOR MATHEUS HERRMANN

## Linguagem utilizada
Java

## Premissas do escalonador
- Quantum: 3 unidades de tempo
- Número máximo de processos: 8
- Tempos de CPU:   Gerados aleatoriamente entre 5 a 15.
- Tempos de I/O: 
 - Imperssora:     5 unidades
 - Disco:          8 unidades
 - Fita magnética: 3 unidades
- Critério de geração dos processos: São gerados no inicio da simulação. Para testes estamos usando um grupo de processos gerados manualmente.
- Semente aleatória, se aplicável: 3.

## Como executar o projeto
Abrir o terminal na pasta raiz do projeto, compile e execute:  
javac Main.java  
java Main  

# Bônus Docker
docker build -t so-escalonador-grupo-4 .  
docker run --rm so-escalonador-grupo-4  


# O que aparece na saída
Admissão do processo: Quando ele entra no sistema e para qual fila é direcionado.  
Execução: CPU seleciona o processo, exibe o tempo restante dele.  
Preempção: O quantum do processo acabou, ele é rebaixado.  
Bloqueio por I/O: Processo sai da CPU e vai para a fila aguardar.  
Retorno de I/O: Processo volta para uma fila de prontos (Disco vai para baixa; Fita/impresso vão para alta)  
Finalização: Tempo de CPU chega a zero, exibe o turnaround.  
  
# Limitações conhecidas
Liste pontos que o grupo não conseguiu implementar ou simplificações realizadas.
