
_main:

;SmartCharger.c,4 :: 		void main(void) {
;SmartCharger.c,6 :: 		OSCCON = 0x71;   // 8MHz internal clock
	MOVLW      113
	MOVWF      OSCCON+0
;SmartCharger.c,7 :: 		CMCON0 = 0x07;   // Disable comperator
	MOVLW      7
	MOVWF      CMCON0+0
;SmartCharger.c,8 :: 		ANSEL = 0x00;    // Disable analog pins
	CLRF       ANSEL+0
;SmartCharger.c,10 :: 		TRISC.B2 = 0x0; // Output on RC2 for MOSFET
	BCF        TRISC+0, 2
;SmartCharger.c,13 :: 		UART1_Init(9600);
	MOVLW      51
	MOVWF      SPBRG+0
	BSF        TXSTA+0, 2
	CALL       _UART1_Init+0
;SmartCharger.c,16 :: 		Delay_ms(200);
	MOVLW      3
	MOVWF      R11+0
	MOVLW      8
	MOVWF      R12+0
	MOVLW      119
	MOVWF      R13+0
L_main0:
	DECFSZ     R13+0, 1
	GOTO       L_main0
	DECFSZ     R12+0, 1
	GOTO       L_main0
	DECFSZ     R11+0, 1
	GOTO       L_main0
;SmartCharger.c,19 :: 		cmd = 0;
	CLRF       _cmd+0
;SmartCharger.c,20 :: 		PORTC.B2 = 1;
	BSF        PORTC+0, 2
;SmartCharger.c,22 :: 		while (1) {
L_main1:
;SmartCharger.c,25 :: 		if (UART1_Data_Ready())
	CALL       _UART1_Data_Ready+0
	MOVF       R0+0, 0
	BTFSC      STATUS+0, 2
	GOTO       L_main3
;SmartCharger.c,26 :: 		cmd = UART1_Read();
	CALL       _UART1_Read+0
	MOVF       R0+0, 0
	MOVWF      _cmd+0
L_main3:
;SmartCharger.c,29 :: 		if (UART1_Tx_Idle()) {
	CALL       _UART1_Tx_Idle+0
	MOVF       R0+0, 0
	BTFSC      STATUS+0, 2
	GOTO       L_main4
;SmartCharger.c,31 :: 		if (cmd == 'c') {
	MOVF       _cmd+0, 0
	XORLW      99
	BTFSS      STATUS+0, 2
	GOTO       L_main5
;SmartCharger.c,33 :: 		UART1_Write_Text("Charge\n");
	MOVLW      ?lstr1_SmartCharger+0
	MOVWF      FARG_UART1_Write_Text_uart_text+0
	CALL       _UART1_Write_Text+0
;SmartCharger.c,34 :: 		PORTC.B2 = 0;
	BCF        PORTC+0, 2
;SmartCharger.c,36 :: 		}
	GOTO       L_main6
L_main5:
;SmartCharger.c,37 :: 		else if (cmd == 's') {
	MOVF       _cmd+0, 0
	XORLW      115
	BTFSS      STATUS+0, 2
	GOTO       L_main7
;SmartCharger.c,39 :: 		UART1_Write_Text("Stop\n");
	MOVLW      ?lstr2_SmartCharger+0
	MOVWF      FARG_UART1_Write_Text_uart_text+0
	CALL       _UART1_Write_Text+0
;SmartCharger.c,40 :: 		PORTC.B2 = 1;
	BSF        PORTC+0, 2
;SmartCharger.c,42 :: 		}
	GOTO       L_main8
L_main7:
;SmartCharger.c,43 :: 		else if (cmd) {
	MOVF       _cmd+0, 0
	BTFSC      STATUS+0, 2
	GOTO       L_main9
;SmartCharger.c,45 :: 		UART1_Write_Text("Received: ");
	MOVLW      ?lstr3_SmartCharger+0
	MOVWF      FARG_UART1_Write_Text_uart_text+0
	CALL       _UART1_Write_Text+0
;SmartCharger.c,46 :: 		UART1_Write(cmd);
	MOVF       _cmd+0, 0
	MOVWF      FARG_UART1_Write_data_+0
	CALL       _UART1_Write+0
;SmartCharger.c,47 :: 		UART1_Write_Text("\n");
	MOVLW      ?lstr4_SmartCharger+0
	MOVWF      FARG_UART1_Write_Text_uart_text+0
	CALL       _UART1_Write_Text+0
;SmartCharger.c,48 :: 		}
L_main9:
L_main8:
L_main6:
;SmartCharger.c,50 :: 		cmd = 0;
	CLRF       _cmd+0
;SmartCharger.c,53 :: 		}
L_main4:
;SmartCharger.c,57 :: 		}
	GOTO       L_main1
;SmartCharger.c,59 :: 		}
L_end_main:
	GOTO       $+0
; end of _main
