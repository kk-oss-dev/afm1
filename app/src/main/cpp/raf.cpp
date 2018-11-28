#include <stdio.h>
#include <stdlib.h>

int main(int argc, char *argv[]) {
    if (argc != 2) {
        printf("please specify file\n");
        exit(1);
    }
    FILE *f = fopen(argv[1], "r");
    if (f == 0) {
        printf("unable to open %s\n", argv[1]);
        exit(1);
    }

    fseek(f, 0, SEEK_END);
    fprintf(stdout, "%li\n", ftell(f));
    fflush(stdout);

    char *buf = 0;
    int bufsize = 0;

    long long offset;
    int size;

    while (scanf("%lli %i", &offset, &size)) {
        fseek(f, offset, SEEK_SET);
        if (size > bufsize) {
            if (buf != 0)
                free(buf);
            buf = (char *) malloc((size_t) size);
            bufsize = size;
        }
        size_t len = fread(buf, sizeof(char), (size_t) size, f);
        fwrite(buf, sizeof(char), len, stdout);
        fflush(stdout);
    }

    if (buf != 0)
        free(buf);

    return 0;
}
