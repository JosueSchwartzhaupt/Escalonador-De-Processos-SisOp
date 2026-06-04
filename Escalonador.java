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
 *   - I/O disparado  : após consumir metade do tempo de CPU original
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

    // Tipos de I/O (índices)
    static final int IO_NENHUM       = 0;
    static final int IO_DISCO        = 1;
    static final int IO_FITA         = 2;
    static final int IO_IMPRESSORA   = 3;
    static final int[] IO_DURACAO    = {0, 4, 6, 3};  // duração por tipo

    // Status dos processos
    static final int ST_NOVO         = 0;
    static final int ST_PRONTO       = 1;
    static final int ST_EXECUTANDO   = 2;
    static final int ST_BLOQUEADO    = 3;
    static final int ST_FINALIZADO   = 4;

    // Prioridades
    static final int PRIO_ALTA       = 0;
    static final int PRIO_BAIXA      = 1;

    // ----------------------------------------------------------------
    // PCB — arrays paralelos (índice = posição do processo)
    // ----------------------------------------------------------------
    static int[] pid           = new int[MAX_PROC];   // identificador
    static int[] ppid          = new int[MAX_PROC];   // processo pai
    static int[] status        = new int[MAX_PROC];   // estado atual
    static int[] prioridade    = new int[MAX_PROC];   // ALTA ou BAIXA
    static int[] cpuTotal      = new int[MAX_PROC];   // tempo total de CPU
    static int[] cpuRestante   = new int[MAX_PROC];   // tempo restante de CPU
    static int[] tipoIO        = new int[MAX_PROC];   // tipo de I/O pendente
    static int[] ioRestante    = new int[MAX_PROC];   // tempo restante de I/O
    static int[] ioDisparaEm   = new int[MAX_PROC];   // cpu restante em que dispara I/O
    static int[] tickChegada   = new int[MAX_PROC];   // tick de criação
    static int[] tickFim       = new int[MAX_PROC];   // tick de finalização
    static int[] acumEspera    = new int[MAX_PROC];   // ticks acumulados em fila

    // ----------------------------------------------------------------
    // FILAS — arrays circulares (buffer com 1 slot de margem)
    // ----------------------------------------------------------------
    static final int CAP     = MAX_PROC + 4;

    static int[] filaAlta    = new int[CAP];
    static int   altaHead    = 0, altaTail = 0;

    static int[] filaBaixa   = new int[CAP];
    static int   baixaHead   = 0, baixaTail = 0;

    static int[] filaIO      = new int[CAP];
    static int   ioHead      = 0, ioTail    = 0;

    // ----------------------------------------------------------------
    // MÉTRICAS GLOBAIS
    // ----------------------------------------------------------------
    static int metPreempcoes   = 0;
    static int metIODisco      = 0;
    static int metIOFita       = 0;
    static int metIOImpressora = 0;
    static int metCPUOciosa    = 0;

    // ----------------------------------------------------------------
    // OPERAÇÕES DE FILA
    // ----------------------------------------------------------------
    static void pushAlta(int i)  { filaAlta[altaTail]   = i; altaTail   = (altaTail   + 1) % CAP; }
    static int  popAlta()        { int i = filaAlta[altaHead];  altaHead  = (altaHead  + 1) % CAP; return i; }
    static boolean altaVazia()   { return altaHead  == altaTail;  }

    static void pushBaixa(int i) { filaBaixa[baixaTail] = i; baixaTail  = (baixaTail  + 1) % CAP; }
    static int  popBaixa()       { int i = filaBaixa[baixaHead]; baixaHead = (baixaHead + 1) % CAP; return i; }
    static boolean baixaVazia()  { return baixaHead == baixaTail; }

    static void pushIO(int i)    { filaIO[ioTail]       = i; ioTail     = (ioTail     + 1) % CAP; }
    static boolean ioVazia()     { return ioHead    == ioTail;    }

    // ----------------------------------------------------------------
    // NOMES PARA LOG
    // ----------------------------------------------------------------
    static String nomeIO(int t) {
        switch (t) {
            case IO_DISCO:     return "DISCO";
            case IO_FITA:      return "FITA";
            case IO_IMPRESSORA:return "IMPRESSORA";
            default:           return "NENHUM";
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
            // 25% chance de nenhum I/O; demais: disco, fita ou impressora
            int r = rng.nextInt(4);
            tipoIO[i]      = (r == 0) ? IO_NENHUM : r; // 1=disco,2=fita,3=impressora
            ioRestante[i]  = 0;
            // Dispara I/O quando restar metade do tempo de CPU (arredondado)
            ioDisparaEm[i] = (tipoIO[i] != IO_NENHUM) ? cpuTotal[i] / 2 : -1;
            tickChegada[i] = tick;
            tickFim[i]     = -1;
            acumEspera[i]  = 0;
            tick          += rng.nextInt(4); // 0 a 3 unidades entre chegadas
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
                        + " → fila ALTA");
            }
        }
    }

    // ----------------------------------------------------------------
    // ATUALIZAR I/O: decrementar timers e liberar quem terminou
    // ----------------------------------------------------------------
    static void atualizarIO(int tempo) {
        if (ioVazia()) return;

        // Varre o buffer circular e reconstrói sem os que terminaram
        int[] tmp = new int[CAP];
        int   cnt = 0;

        int cur = ioHead;
        while (cur != ioTail) {
            int i = filaIO[cur];
            cur = (cur + 1) % CAP;
            ioRestante[i]--;
            if (ioRestante[i] <= 0) {
                // Libera processo conforme regra de retorno
                status[i] = ST_PRONTO;
                if (tipoIO[i] == IO_DISCO) {
                    prioridade[i] = PRIO_BAIXA;
                    pushBaixa(i);
                    log(tempo, "P" + pid[i] + " retornou do DISCO → fila BAIXA");
                } else {
                    prioridade[i] = PRIO_ALTA;
                    pushAlta(i);
                    log(tempo, "P" + pid[i] + " retornou de "
                            + nomeIO(tipoIO[i]) + " → fila ALTA");
                }
                tipoIO[i] = IO_NENHUM; // I/O já tratado
            } else {
                tmp[cnt++] = i; // ainda aguardando
            }
        }

        // Reconstruir fila de I/O
        ioHead = 0; ioTail = 0;
        for (int k = 0; k < cnt; k++) pushIO(tmp[k]);
    }

    // ----------------------------------------------------------------
    // ACUMULAR ESPERA para processos nas filas
    // ----------------------------------------------------------------
    static void acumularEspera() {
        int cur = altaHead;
        while (cur != altaTail) { acumEspera[filaAlta[cur]]++;  cur = (cur + 1) % CAP; }
        cur = baixaHead;
        while (cur != baixaTail){ acumEspera[filaBaixa[cur]]++; cur = (cur + 1) % CAP; }
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
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║           RESUMO FINAL DA SIMULAÇÃO                  ║");
        System.out.println("╠═══════════════════════════════════════════════════════╣");
        System.out.printf( "║  Tempo total da simulação   : %-5d unidades          ║%n", tempoTotal);
        System.out.printf( "║  Total de preempções        : %-5d                   ║%n", metPreempcoes);
        System.out.printf( "║  Eventos I/O - Disco        : %-5d                   ║%n", metIODisco);
        System.out.printf( "║  Eventos I/O - Fita         : %-5d                   ║%n", metIOFita);
        System.out.printf( "║  Eventos I/O - Impressora   : %-5d                   ║%n", metIOImpressora);
        System.out.printf( "║  Ticks com CPU ociosa       : %-5d (%.1f%%)            ║%n",
                metCPUOciosa, tempoTotal > 0 ? 100.0 * metCPUOciosa / tempoTotal : 0);
        System.out.println("╠═══════════════════════════════════════════════════════╣");
        System.out.println("║  PID   PPID   Turnaround   Espera                    ║");
        System.out.println("║  ───   ────   ──────────   ──────                    ║");

        double somaTA = 0, somaE = 0;
        for (int i = 0; i < MAX_PROC; i++) {
            System.out.printf("║  P%-2d   P%-2d    %6d       %6d                    ║%n",
                    pid[i], ppid[i], tickFim[i] - tickChegada[i], acumEspera[i]);
            somaTA += tickFim[i] - tickChegada[i];
            somaE  += acumEspera[i];
        }
        System.out.println("╠═══════════════════════════════════════════════════════╣");
        System.out.printf( "║  Turnaround médio : %-6.2f                            ║%n", somaTA / MAX_PROC);
        System.out.printf( "║  Espera média     : %-6.2f                            ║%n", somaE  / MAX_PROC);
        System.out.println("╚═══════════════════════════════════════════════════════╝");
    }

    // ----------------------------------------------------------------
    // MAIN — LOOP PRINCIPAL
    // ----------------------------------------------------------------
    public static void main(String[] args) {
        Random rng = new Random(SEED);

        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   Simulador Round Robin com Feedback — Feevale SO    ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.printf("[config] quantum=%d | processos=%d | seed=%d%n%n",
                QUANTUM, MAX_PROC, SEED);

        inicializar(rng);

        int tempo = 0;

        while (haoPendentes()) {

            // 1. Admitir novos processos que chegaram até este tick
            admitirNovos(tempo);

            // 2. Decrementar timers de I/O e liberar prontos
            atualizarIO(tempo);

            // 3. Acumular tempo de espera para processos em fila
            acumularEspera();

            // 4. Selecionar processo para executar
            int proc = -1;
            if (!altaVazia()) {
                proc = popAlta();
            } else if (!baixaVazia()) {
                proc = popBaixa();
            }

            // 5. CPU ociosa?
            if (proc == -1) {
                log(tempo, "── CPU ociosa ──");
                metCPUOciosa++;
                tempo++;
                continue;
            }

            // 6. Executar por até QUANTUM ticks
            status[proc] = ST_EXECUTANDO;
            log(tempo, "CPU executa P" + pid[proc]
                    + " [prio=" + nomePrio(prioridade[proc])
                    + " cpu_rest=" + cpuRestante[proc] + "]");

            int exec      = 0;
            boolean fim   = false;
            boolean bloq  = false;

            while (exec < QUANTUM) {
                cpuRestante[proc]--;
                exec++;
                tempo++;

                // Processar chegadas e I/O a cada tick
                admitirNovos(tempo);
                atualizarIO(tempo);
                acumularEspera();

                // Processo finalizou CPU?
                if (cpuRestante[proc] == 0) {
                    fim = true;
                    break;
                }

                // Disparo de I/O: quando cpuRestante atingir o ponto configurado
                if (tipoIO[proc] != IO_NENHUM && cpuRestante[proc] == ioDisparaEm[proc]) {
                    bloq = true;
                    break;
                }
            }

            // 7. Tratar resultado da execução
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
                log(tempo, "P" + pid[proc] + " → I/O " + nomeIO(tipoIO[proc])
                        + " (" + IO_DURACAO[tipoIO[proc]] + " unid) [cpu_rest=" + cpuRestante[proc] + "]");

            } else {
                // Preempção por fim de quantum
                status[proc]    = ST_PRONTO;
                prioridade[proc] = PRIO_BAIXA;
                pushBaixa(proc);
                metPreempcoes++;
                log(tempo, "P" + pid[proc] + " preemptado → fila BAIXA");
            }
        }

        resumo(tempo);
    }
}
