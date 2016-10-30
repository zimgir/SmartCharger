
char cmd;

void main(void) {

        OSCCON = 0x71;   // 8MHz internal clock
        CMCON0 = 0x07;   // Disable comperator
        ANSEL = 0x00;    // Disable analog pins

        TRISC.B2 = 0x0; // Output on RC2 for MOSFET

        // Initialize hardware UART1 and establish communication at 9600 bps
        UART1_Init(9600);

        // Delay for init stability
        Delay_ms(200);

        // Init variables
        cmd = 0;
        PORTC.B2 = 1;

        while (1) {

                // If data is ready, read it:
                if (UART1_Data_Ready())
                        cmd = UART1_Read();

                // If the previous data has been shifted out, send next data:
                if (UART1_Tx_Idle()) {

                        if (cmd == 'c') {

                                UART1_Write_Text("Charge\n");
                                PORTC.B2 = 0;

                        }
                        else if (cmd == 's') {

                                UART1_Write_Text("Stop\n");
                                PORTC.B2 = 1;

                        }
                        else if (cmd) {

                                UART1_Write_Text("Received: ");
                                UART1_Write(cmd);
                                UART1_Write_Text("\n");
                        }

                        cmd = 0;


                }



        }

}