import java.util.Random;

/**
 * Simulador de Escalonamento Round Robin com Feedback
 * Disciplina: Sistemas Operacionais — Feevale
 *
 * Premissas:
 *   - Quantum        : 3 unidades de tempo
 *   - Max processos  : 8
 *   - Tempo CPU      : 5 a 15 unidades
 *   - I/O disco      : 4 unidades
 *   - I/O fita       : 6 unidades
 *   - I/O impressora : 3 unidades
 *   - Chegada        : a cada 0 a 3 unidades de tempo
 *   - Semente        : 42
 *   - I/O disparado  : quando restar metade do tempo de CPU original
 *
 * Implementado sem modelagem OO: apenas classe principal,
 * métodos estáticos e arrays paralelos (PCB).
 */
public class Escalonador {

    // ----------------------------------------------------------------
    // CONSTANTES
    // ----------------------------------------------------------------
    static final int MAX_PROC        = 8;
    static final int QUANTUM         = 3;
    static final int SEED            = 42;
    static final int CPU_MIN         = 5;
    static final int CPU_MAX         = 15;

    // Tipos de I/O
    static final int IO_NENHUM       = 0;
    static final int IO_DISCO        = 1;
    static final int IO_FITA         = 2;
    static final int IO_IMPRESSORA   = 3;
    static final int[] IO_DURACAO    = {0, 4, 6, 3};

    // Status
    static final int ST_NOVO         = 0;
    static final int ST_PRONTO       = 1;
    static final int ST_EXECUTANDO   = 2;
    static final int ST_BLOQUEADO    = 3;
    static final int ST_FINALIZADO   = 4;

    // Prioridades
    static final int PRIO_ALTA       = 0;
    static final int PRIO_BAIXA      = 1;

    // ----------------------------------------------------------------
    // PCB — arrays paralelos
    // ----------------------------------------------------------------
    static int[] pid           = new int[MAX_PROC];
    static int[] ppid          = new int[MAX_PROC];
    static int[] status        = new int[MAX_PROC];
    static int[] prioridade    = new int[MAX_PROC];
    static int[] cpuTotal      = new int[MAX_PROC];
    static int[] cpuRestante   = new int[MAX_PROC];
    static int[] tipoIO        = new int[MAX_PROC];
    static int[] ioRestante    = new int[MAX_PROC];
    static int[] ioDisparaEm   = new int[MAX_PROC];
    static int[] tickChegada   = new int[MAX_PROC];
    static int[] tickFim       = new int[MAX_PROC];
    static int[] acumEspera    = new int[MAX_PROC];

    // ----------------------------------------------------------------
    // FILAS — arrays simples (não circulares) com ponteiro de tamanho
    // ----------------------------------------------------------------
    static final int CAP     = MAX_PROC + 2;

    static int[] filaAlta    = new int[CAP];
    static int   altaSize    = 0;

    static int[] filaBaixa   = new int[CAP];
    static int   baixaSize   = 0;

    // Fila de I/O: guardamos índice do processo e seu ioRestante
    // diretamente nos arrays do PCB; a fila só lista quais estão bloqueados
    static int[] filaIO      = new int[CAP];
    static int   ioSize      = 0;

    // ----------------------------------------------------------------
    // OPERAÇÕES DE FILA (arrays simples com shift)
    // ----------------------------------------------------------------
    static void pushAlta(int i)  { filaAlta[altaSize++]   = i; }
    static int  popAlta()        { int v = filaAlta[0]; System.arraycopy(filaAlta,  1, filaAlta,  0, --altaSize);  return v; }
    static boolean altaVazia()   { return altaSize  == 0; }

    static void pushBaixa(int i) { filaBaixa[baixaSize++] = i; }
    static int  popBaixa()       { int v = filaBaixa[0]; System.arraycopy(filaBaixa, 1, filaBaixa, 0, --baixaSize); return v; }
    static boolean baixaVazia()  { return baixaSize == 0; }

    static void pushIO(int i)    { filaIO[ioSize++]       = i; }
    static boolean ioVazia()     { return ioSize    == 0; }

    // Remove elemento na posição k da fila de I/O
    static void removeIO(int k)  { System.arraycopy(filaIO, k + 1, filaIO, k, --ioSize); }

    // ----------------------------------------------------------------
    // NOMES PARA LOG
    // ----------------------------------------------------------------
    static String nomeIO(int t) {
        switch (t) {
            case IO_DISCO:      return "DISCO";
            case IO_FITA:       return "FITA";
            case IO_IMPRESSORA: return "IMPRESSORA";
            default:            return "NENHUM";
        }
    }
    static String nomePrio(int p) { return p == PRIO_ALTA ? "ALTA" : "BAIXA"; }

    static void log(int t, String msg) {
        System.out.printf("[t=%03d] %s%n", t, msg);
    }

    // ----------------------------------------------------------------
    // INICIALIZAÇÃO
    // ----------------------------------------------------------------
    static void inicializar(Random rng) {
        int tick = 0;
        for (int i = 0; i < MAX_PROC; i++) {
            pid[i]         = i + 1;
            ppid[i]        = (i == 0) ? 0 : pid[rng.nextInt(i)];
            status[i]      = ST_NOVO;
            prioridade[i]  = PRIO_ALTA;
            cpuTotal[i]    = CPU_MIN + rng.nextInt(CPU_MAX - CPU_MIN + 1);
            cpuRestante[i] = cpuTotal[i];
            int r          = rng.nextInt(4);
            tipoIO[i]      = (r == 0) ? IO_NENHUM : r;
            ioRestante[i]  = 0;
            ioDisparaEm[i] = (tipoIO[i] != IO_NENHUM) ? cpuTotal[i] / 2 : -1;
            tickChegada[i] = tick;
            tickFim[i]     = -1;
            acumEspera[i]  = 0;
            tick          += rng.nextInt(4);
        }
    }

    // ----------------------------------------------------------------
    // ADMISSÃO: mover processos NOVOS que chegaram até 'tempo'
    // ----------------------------------------------------------------
    static void admitirNovos(int tempo) {
        for (int i = 0; i < MAX_PROC; i++) {
            if (status[i] == ST_NOVO && tickChegada[i] <= tempo) {
                status[i] = ST_PRONTO;
                pushAlta(i);
                log(tempo, "P" + pid[i] + " admitido"
                        + " [cpu=" + cpuTotal[i]
                        + " io=" + nomeIO(tipoIO[i]) + "]"
                        + " -> fila ALTA");
            }
        }
    }

    // ----------------------------------------------------------------
    // ATUALIZAR I/O: decrementar timers e liberar quem terminou
    // ----------------------------------------------------------------
    static void atualizarIO(int tempo) {
        int k = 0;
        while (k < ioSize) {
            int i = filaIO[k];
            ioRestante[i]--;
            if (ioRestante[i] <= 0) {
                // Processo terminou I/O
                status[i] = ST_PRONTO;
                if (tipoIO[i] == IO_DISCO) {
                    prioridade[i] = PRIO_BAIXA;
                    pushBaixa(i);
                    log(tempo, "P" + pid[i] + " retornou do DISCO -> fila BAIXA");
                } else {
                    prioridade[i] = PRIO_ALTA;
                    pushAlta(i);
                    log(tempo, "P" + pid[i] + " retornou de "
                            + nomeIO(tipoIO[i]) + " -> fila ALTA");
                }
                tipoIO[i] = IO_NENHUM;
                removeIO(k); // remove e não avança k (o próximo desceu)
            } else {
                k++;
            }
        }
    }

    // ----------------------------------------------------------------
    // ACUMULAR ESPERA para processos nas filas prontas
    // ----------------------------------------------------------------
    static void acumularEspera() {
        for (int k = 0; k < altaSize;  k++) acumEspera[filaAlta[k]]++;
        for (int k = 0; k < baixaSize; k++) acumEspera[filaBaixa[k]]++;
    }

    // ----------------------------------------------------------------
    // VERIFICA SE AINDA HÁ PROCESSOS NÃO FINALIZADOS
    // ----------------------------------------------------------------
    static boolean haoPendentes() {
        for (int i = 0; i < MAX_PROC; i++)
            if (status[i] != ST_FINALIZADO) return true;
        return false;
    }

    // ----------------------------------------------------------------
    // IMPRIMIR RESUMO FINAL
    // ----------------------------------------------------------------
    static void resumo(int tempoTotal) {
        System.out.println();
        System.out.println("+=======================================================+");
        System.out.println("|           RESUMO FINAL DA SIMULACAO                  |");
        System.out.println("+=======================================================+");
        System.out.printf( "|  Tempo total da simulacao   : %-5d unidades          |%n", tempoTotal);
        System.out.printf( "|  Total de preempcoes        : %-5d                   |%n", metPreempcoes);
        System.out.printf( "|  Eventos I/O - Disco        : %-5d                   |%n", metIODisco);
        System.out.printf( "|  Eventos I/O - Fita         : %-5d                   |%n", metIOFita);
        System.out.printf( "|  Eventos I/O - Impressora   : %-5d                   |%n", metIOImpressora);
        System.out.printf( "|  Ticks com CPU ociosa       : %-5d (%.1f%%)            |%n",
                metCPUOciosa, tempoTotal > 0 ? 100.0 * metCPUOciosa / tempoTotal : 0);
        System.out.println("+=======================================================+");
        System.out.println("|  PID   PPID   Turnaround   Espera                    |");
        System.out.println("|  ---   ----   ----------   ------                    |");

        double somaTA = 0, somaE = 0;
        for (int i = 0; i < MAX_PROC; i++) {
            int ta = (tickFim[i] >= 0) ? tickFim[i] - tickChegada[i] : -1;
            System.out.printf("|  P%-2d   P%-2d    %6d       %6d                    |%n",
                    pid[i], ppid[i], ta, acumEspera[i]);
            somaTA += ta;
            somaE  += acumEspera[i];
        }
        System.out.println("+=======================================================+");
        System.out.printf( "|  Turnaround medio : %-6.2f                            |%n", somaTA / MAX_PROC);
        System.out.printf( "|  Espera media     : %-6.2f                            |%n", somaE  / MAX_PROC);
        System.out.println("+=======================================================+");
    }

    // ----------------------------------------------------------------
    // MÉTRICAS GLOBAIS
    // ----------------------------------------------------------------
    static int metPreempcoes   = 0;
    static int metIODisco      = 0;
    static int metIOFita       = 0;
    static int metIOImpressora = 0;
    static int metCPUOciosa    = 0;

    // ----------------------------------------------------------------
    // MAIN — LOOP PRINCIPAL
    // ----------------------------------------------------------------
    public static void main(String[] args) {
        Random rng = new Random(SEED);

        System.out.println("+-------------------------------------------------------+");
        System.out.println("|   Simulador Round Robin com Feedback - Feevale SO    |");
        System.out.println("+-------------------------------------------------------+");
        System.out.printf("[config] quantum=%d | processos=%d | seed=%d%n%n",
                QUANTUM, MAX_PROC, SEED);

        inicializar(rng);

        int tempo = 0;

        while (haoPendentes()) {

            // 1. Admitir novos processos que chegaram até este tick
            admitirNovos(tempo);

            // 2. Decrementar timers de I/O e liberar prontos
            atualizarIO(tempo);

            // 3. Acumular tempo de espera de quem está nas filas
            acumularEspera();

            // 4. Selecionar processo
            int proc = -1;
            if (!altaVazia()) {
                proc = popAlta();
            } else if (!baixaVazia()) {
                proc = popBaixa();
            }

            // 5. CPU ociosa?
            if (proc == -1) {
                log(tempo, "-- CPU ociosa --");
                metCPUOciosa++;
                tempo++;
                continue;
            }

            // 6. Executar por até QUANTUM ticks
            status[proc] = ST_EXECUTANDO;
            log(tempo, "CPU executa P" + pid[proc]
                    + " [prio=" + nomePrio(prioridade[proc])
                    + " cpu_rest=" + cpuRestante[proc] + "]");

            int exec     = 0;
            boolean fim  = false;
            boolean bloq = false;

            while (exec < QUANTUM) {
                cpuRestante[proc]--;
                exec++;
                tempo++;

                admitirNovos(tempo);
                atualizarIO(tempo);
                acumularEspera();

                if (cpuRestante[proc] == 0) {
                    fim = true;
                    break;
                }

                if (tipoIO[proc] != IO_NENHUM && cpuRestante[proc] == ioDisparaEm[proc]) {
                    bloq = true;
                    break;
                }
            }

            // 7. Tratar resultado
            if (fim) {
                status[proc]  = ST_FINALIZADO;
                tickFim[proc] = tempo;
                log(tempo, "P" + pid[proc] + " FINALIZADO"
                        + " [turnaround=" + (tickFim[proc] - tickChegada[proc]) + "]");

            } else if (bloq) {
                status[proc]     = ST_BLOQUEADO;
                ioRestante[proc] = IO_DURACAO[tipoIO[proc]];
                pushIO(proc);
                switch (tipoIO[proc]) {
                    case IO_DISCO:      metIODisco++;      break;
                    case IO_FITA:       metIOFita++;       break;
                    case IO_IMPRESSORA: metIOImpressora++; break;
                }
                log(tempo, "P" + pid[proc] + " -> I/O " + nomeIO(tipoIO[proc])
                        + " (" + IO_DURACAO[tipoIO[proc]] + " unid)"
                        + " [cpu_rest=" + cpuRestante[proc] + "]");

            } else {
                // Preempcao por fim de quantum
                status[proc]     = ST_PRONTO;
                prioridade[proc] = PRIO_BAIXA;
                pushBaixa(proc);
                metPreempcoes++;
                log(tempo, "P" + pid[proc] + " preemptado -> fila BAIXA");
            }
        }

        resumo(tempo);
    }
}
