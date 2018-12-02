#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utime.h>
#include <errno.h>
#include <fcntl.h>

void exit(int code, const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    vfprintf(stderr, msg, args);
    va_end(args);
    fprintf(stderr, " (%d)", code);
    fflush(stderr);
    exit(code);
}

void exit(FILE *f, const char *msg, ...) {
    int code = ferror(f);
    va_list args;
    va_start(args, msg);
    vfprintf(stderr, msg, args);
    va_end(args);
    fprintf(stderr, " (%d)", code);
    fflush(stderr);
    exit(code);
}

void putline(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vfprintf(stdout, fmt, args);
    va_end(args);
    fputc(0, stdout);
    fflush(stdout);
}

ssize_t getline(char **pbuf, size_t *pbufs) {
    char *buf = *pbuf;
    size_t len = 0;
    int c;

    for (;;) {
        c = fgetc(stdin);
        if (c == EOF)
            return EOF;

        if (len == *pbufs) {
            if (buf == NULL)
                buf = (char *) malloc(*pbufs = 100);
            else
                buf = (char *) realloc(buf, *pbufs *= 2);
            if (buf == NULL) {
                free(*pbuf);
                *pbuf = NULL;
                return EOF;
            }
            *pbuf = buf;
        }

        if ((buf[len++] = c) == '\0')
            return len;
    }
}

char *readlink(char *name) {
    char *buf = (char *) malloc(PATH_MAX + 1);
    ssize_t len = readlink(name, buf, PATH_MAX);
    if (len == -1)
        exit(errno, "unable to readlink '%s'", name);
    buf[len] = 0; // readlink does not append a null byte to buf
    return buf;
}

bool exists(char *buf) {
    return access(buf, F_OK) != -1;
}

bool isdir(char *buf) {
    if (!exists(buf))
        return false;
    struct stat st;
    if (stat(buf, &st) != 0)
        exit(errno, "isdir unable to stat '%s'", buf);
    return S_ISDIR(st.st_mode);
}

bool islink(char *buf) {
    if (!exists(buf))
        return false;
    struct stat st;
    if (stat(buf, &st) != 0)
        exit(errno, "islink unable to stat '%s'", buf);
    return S_ISLNK(st.st_mode);
}

char *followlink(char *buf) {
    char *f = NULL;
    while (islink(buf)) {
        if (f != NULL)
            free(f);
        buf = f = readlink(buf);
    }
    return buf;
}

long long getsize(char *buf) {
    if (!exists(buf))
        return -1;
    char *f = NULL;
    while (islink(buf)) {
        if (f != NULL)
            free(f);
        buf = f = readlink(buf);
    }
    struct stat st;
    if (stat(buf, &st) != 0)
        exit(errno, "getsize unable to stat '%s'", buf);
    if (f != NULL)
        free(f);
    return st.st_size;
}

long getlast(char *buf) {
    if (!exists(buf))
        return -1;
    char *f = NULL;
    while (islink(buf)) {
        if (f != NULL)
            free(f);
        buf = f = readlink(buf);
    }
    struct stat st;
    if (stat(buf, &st) != 0)
        exit(errno, "getlast unable to stat '%s'", buf);
    if (f != NULL)
        free(f);
    return st.st_mtime * 1000;
}

void touch(char *buf, time_t time) {
    if (!exists(buf)) {
        int fd = open(buf, O_RDWR | O_CREAT, S_IRUSR | S_IRGRP | S_IROTH);
        if (fd == -1)
            exit(errno, "unable to create empty file %s", buf);
        close(fd);
    }
    struct stat st = {0};
    struct utimbuf new_times = {0};
    if (stat(buf, &st) != 0)
        exit(errno, "touch unable to stat '%s'", buf);
    new_times.actime = st.st_atime;
    new_times.modtime = time;
    if (utime(buf, &new_times) != 0)
        exit(errno, "touch unable to utime '%s'", buf);
}

int main(int argc, char *argv[]) {
    FILE *f = NULL;

    char *buf = NULL;
    size_t bufs = 0;

    while (getline(&buf, &bufs) > 0) {
        if (strcmp(buf, "lsa") == 0) {
            if (getline(&buf, &bufs) > 0) {
                DIR *dp;
                dp = opendir(buf);
                if (dp != NULL) {
                    struct dirent *ep;
                    while ((ep = readdir(dp))) {
                        char path[PATH_MAX + 1];
                        snprintf(path, sizeof(path), "%s/%s", buf, ep->d_name);
                        struct stat st = {0};
                        stat(path, &st); // failed for non existent links
                        char *link = NULL;
                        switch (ep->d_type) {
                            case DT_DIR: {
                                putline("d");
                                break;
                            }
                            case DT_LNK: {
                                link = readlink(path);
                                char *target = followlink(link);
                                if (isdir(target)) {
                                    putline("ld");
                                } else {
                                    putline("lf");
                                    st.st_size = getsize(target);
                                }
                                if (target != link)
                                    free(target);
                                break;
                            }
                            default: {
                                putline("f");
                            }
                        }
                        putline("%lli", (long long) st.st_size);
                        putline("%li", (long) st.st_mtime * 1000);
                        putline(ep->d_name);
                        if (link != NULL) {
                            putline(link);
                            free(link);
                        }
                    }
                    closedir(dp);
                    putline("EOF");
                } else {
                    exit(errno, "Couldn't open the directory '%s'", buf);
                }
            }
            continue;
        }
        if (strcmp(buf, "rafopen") == 0) {
            char *target;
            if (getline(&buf, &bufs) > 0) {
                target = strdup(buf);
                if (getline(&buf, &bufs) > 0) {
                    if (f != NULL)
                        fclose(f);
                    f = fopen(target, buf);
                    if (f == NULL)
                        exit(errno, "unable to open '%s'", target);
                    if (strcmp(buf, "rb") == 0) {
                        if (fseek(f, 0, SEEK_END) != 0)
                            exit(f, "unable to seek '%s'", buf);
                        putline("%li", ftell(f));
                    } else {
                        putline("ok");
                    }
                }
                free(target);
            }
            continue;
        }
        if (strcmp(buf, "rafclose") == 0) {
            if (f == NULL)
                exit(1, "file NULL");
            fclose(f);
            f = NULL;
            putline("ok");
            continue;
        }
        if (strcmp(buf, "rafread") == 0) {
            long long offset;
            size_t size;
            if (getline(&buf, &bufs) > 0) {
                offset = atoll(buf);
                if (getline(&buf, &bufs) > 0) {
                    size = (size_t) atoi(buf);
                    if (fseek(f, offset, SEEK_SET) != 0)
                        exit(f, "unable to seek");
                    if (size > bufs) {
                        if (buf != 0)
                            free(buf);
                        buf = (char *) malloc((size_t) size);
                        bufs = (size_t) size;
                    }
                    while (size > 0) {
                        size_t len = fread(buf, sizeof(char), (size_t) size, f);
                        if (len <= 0)
                            exit(f, "unable to read file");
                        size_t w = fwrite(buf, sizeof(char), len, stdout);
                        if (w != len)
                            exit(f, "unable to write stdout");
                        size -= len;
                    }
                    fflush(stdout);
                }
            }
            continue;
        }
        if (strcmp(buf, "rafwrite") == 0) {
            long long offset;
            size_t size;
            if (getline(&buf, &bufs) > 0) {
                offset = atoll(buf);
                if (getline(&buf, &bufs) > 0) {
                    size = (size_t) atoi(buf);
                    if (fseek(f, offset, SEEK_SET) != 0)
                        exit(f, "unable to seek");
                    if (size > bufs) {
                        if (buf != 0)
                            free(buf);
                        buf = (char *) malloc((size_t) size);
                        bufs = (size_t) size;
                    }
                    while (size > 0) {
                        size_t len = fread(buf, sizeof(char), size, stdin);
                        if (len <= 0)
                            exit(stdin, "unable to read stdin");
                        size_t w = fwrite(buf, sizeof(char), len, f);
                        if (w != len)
                            exit(f, "unable to write file");
                        size -= len;
                    }
                    putline("ok");
                }
            }
            continue;
        }
        if (strcmp(buf, "isdir") == 0) {
            if (getline(&buf, &bufs) > 0) {
                if (isdir(buf))
                    putline("true");
                else
                    putline("false");
            }
            continue;
        }
        if (strcmp(buf, "exists") == 0) {
            if (getline(&buf, &bufs) > 0) {
                if (exists(buf))
                    putline("true");
                else
                    putline("false");
            }
            continue;
        }
        if (strcmp(buf, "delete") == 0) {
            if (getline(&buf, &bufs) > 0) {
                if (remove(buf) != 0)
                    exit(errno, "unable remove %s", buf);
                else
                    putline("ok");
            }
            continue;
        }
        if (strcmp(buf, "mkdir") == 0) {
            if (getline(&buf, &bufs) > 0) {
                if (mkdir(buf, 0755) != 0)
                    exit(errno, "unable mkdir %s", buf);
                else
                    putline("ok");
            }
            continue;
        }
        if (strcmp(buf, "length") == 0) {
            if (getline(&buf, &bufs) > 0)
                putline("%lli", getsize(buf));
            continue;
        }
        if (strcmp(buf, "last") == 0) {
            if (getline(&buf, &bufs) > 0)
                putline("%li", getlast(buf));
            continue;
        }
        if (strcmp(buf, "rename") == 0) {
            if (getline(&buf, &bufs) > 0) {
                char *target = strdup(buf);
                if (getline(&buf, &bufs) > 0) {
                    if (rename(target, buf) != 0)
                        exit(errno, "unable rename '%s' '%s'", target, buf);
                    else
                        putline("ok");
                }
                free(target);
            }
            continue;
        }
        if (strcmp(buf, "ln") == 0) {
            if (getline(&buf, &bufs) > 0) {
                char *target = strdup(buf);
                if (getline(&buf, &bufs) > 0) {
                    if (symlink(target, buf) != 0)
                        exit(errno, "unable ln '%s' '%s'", target, buf);
                    else
                        putline("ok");
                }
                free(target);
            }
            continue;
        }
        if (strcmp(buf, "touch") == 0) {
            char *target;
            if (getline(&buf, &bufs) > 0) {
                target = strdup(buf);
                if (getline(&buf, &bufs) > 0) {
                    touch(target, atol(buf));
                    putline("ok");
                }
                free(target);
            }
            continue;
        }
        if (strcmp(buf, "ping") == 0) {
            putline("pong");
            continue;
        }
        if (strcmp(buf, "exit") == 0)
            break;
        exit(1, "unknown command %s", buf);
    }

    if (buf != 0)
        free(buf);

    return 0;
}
