format short; format compact; clear; clc;

% wav file parameters

Fs=44100; %Sampling rate
N=16; % Resolution
A=0.5*(2^N)-1; % Max Amplitude

% Signal parameters

fs=1e3; % Signal frequency
Ts=500e-3; % Data period

% Creating the signal

ts=linspace(0,Ts,Fs*Ts); % Sampling points

y(:,1)=A*sin(2*pi*fs*ts); % Stereo samples vector
y(:,2)=y(:,1);

wavwrite(y,Fs,N,'switch_sound');





