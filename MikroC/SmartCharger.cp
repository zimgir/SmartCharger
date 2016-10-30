#line 1 "E:/Projects/PIC/MikroC/BT Smart Charger/SmartCharger.c"

char cmd;

void main(void) {

 OSCCON = 0x71;
 CMCON0 = 0x07;
 ANSEL = 0x00;

 TRISC.B2 = 0x0;


 UART1_Init(9600);


 Delay_ms(200);


 cmd = 0;
 PORTC.B2 = 1;

 while (1) {


 if (UART1_Data_Ready())
 cmd = UART1_Read();


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
