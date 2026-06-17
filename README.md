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
Clone o repositório:
`git clone <URL_DO_REPOSITORIO>`
`cd Escalonador-De-Processos-SisOp`
Certifique-se de possuir o Java 21 e o Maven instalados.
Abra o terminal na pasta raiz do projeto, compile e execute:

Utilizando Maven (recomendado):

`mvn clean package`
`java -jar target/*.jar`

Ou diretamente com o Java:

`javac src/main/java/com/sisop/Main.java`
`java src/main/java/com/sisop/Main.java`

Também é possível abrir o projeto em uma IDE compatível com Maven (IntelliJ IDEA, Eclipse ou VS Code) e executar a classe Main localizada em src/main/java/com/sisop/Main.java.

# Bônus Docker
Caso possua o Docker instalado, é possível executar a aplicação sem instalar Java ou Maven localmente.

`docker build -t so-escalonador-grupo-4 .`
`docker run --rm so-escalonador-grupo-4  `


# O que aparece na saída
A saída apresenta a linha do tempo da simulação, mostrando a criação dos processos, execução na CPU, solicitações e retornos de I/O, preempções, mudanças de fila, períodos de CPU ociosa e finalização dos processos. Ao final, é exibido um resumo com tempo de espera, turnaround, número de preempções e estatísticas gerais da simulação.
  
# Limitações conhecidas
Não há encapsulamento do PCB em uma classe específica.
Não foi implementada a relação pai-filho entre processos (PPID).
O sistema de I/O processa apenas uma operação por vez.
Não há mecanismo de aging nem terceira fila de prioridade.
A visualização é apenas textual, sem interface gráfica ou diagrama de Gantt.
