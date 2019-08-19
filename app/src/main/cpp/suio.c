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
#include <stdbool.h>
#include <linux/fs.h>
#include <sys/statfs.h>
#include <sys/system_properties.h>
#include <pwd.h>
#include <grp.h>

typedef struct {
    char *buf;
    size_t bufn;
} string;

int SDK_INT() {
    char sdk_ver_str[PROP_NAME_MAX] = "0";
    __system_property_get("ro.build.version.sdk", sdk_ver_str);
    return atoi(sdk_ver_str);
}

void exitn(int code, const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    vfprintf(stderr, msg, args);
    va_end(args);
    fprintf(stderr, ", %s", strerror(errno));
    fflush(stderr);
    exit(code);
}

void exitf(FILE *f, const char *msg, ...) {
    int code = ferror(f);
    va_list args;
    va_start(args, msg);
    vfprintf(stderr, msg, args);
    va_end(args);
    fprintf(stderr, " (%d)", code);
    fflush(stderr);
    exit(code);
}

void pathjoin(char *str, size_t strn, string *dir, char *name) {
    size_t len = strlen(dir->buf);
    strncpy(str, dir->buf, strn);
    str += len;
    strn -= len;
    if (len > 0 && dir->buf[len - 1] != '/') {
        strncpy(str, "/", strn);
        str += 1;
        strn -= 1;
    }
    strncpy(str, name, strn);
}

void writestring(const char *str) {
    fputs(str, stdout);
    fputc(0, stdout);
    fflush(stdout);
}

void writestringf(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vfprintf(stdout, fmt, args);
    va_end(args);
    fputc(0, stdout);
    fflush(stdout);
}

ssize_t readstring(string *str) {
    size_t len = 0;
    int c;

    for (;;) {
        c = fgetc(stdin);
        if (c == EOF)
            return EOF;

        if (len == str->bufn) {
            char *buf = str->buf;
            size_t bufs = str->bufn;
            if (buf == NULL)
                buf = (char *) malloc(bufs = 100);
            else
                buf = (char *) realloc(buf, bufs *= 2);
            if (buf == NULL) {
                free(str->buf);
                str->buf = NULL;
                return EOF;
            }
            str->buf = buf;
            str->bufn = bufs;
        }

        if ((str->buf[len++] = (char) c) == '\0')
            return len;
    }
}

bool exists(char *buf) {
    return access(buf, F_OK) != -1;
}

bool isdir(char *buf) {
    if (!exists(buf))
        return false;
    struct stat st;
    if (stat(buf, &st) != 0)
        exitn(errno, "isdir unable to stat %s", buf);
    return S_ISDIR(st.st_mode);
}

bool islink(char *buf) {
    if (!exists(buf))
        return false;
    struct stat st;
    if (stat(buf, &st) != 0)
        exitn(errno, "islink unable to stat %s", buf);
    return S_ISLNK(st.st_mode);
}

char *getlink(char *name) {
    char *buf = (char *) malloc(PATH_MAX + 1);
    ssize_t len = readlink(name, buf, PATH_MAX);
    if (len == -1)
        exitn(errno, "unable to readlink %s", name);
    buf[len] = 0; // readlink does not append a null byte to buf
    return buf;
}

char *followlink(char *buf) {
    char *f = NULL;
    while (islink(buf)) {
        if (f != NULL)
            free(f);
        buf = f = getlink(buf);
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
        buf = f = getlink(buf);
    }
    struct stat st;
    if (stat(buf, &st) != 0)
        exitn(errno, "getsize unable to stat %s", buf);
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
        buf = f = getlink(buf);
    }
    struct stat st;
    if (stat(buf, &st) != 0)
        exitn(errno, "getlast unable to stat %s", buf);
    if (f != NULL)
        free(f);
    return st.st_mtime * 1000;
}

void touch(char *buf, time_t time) {
    if (!exists(buf)) {
        int fd = open(buf, O_RDWR | O_CREAT, S_IRUSR | S_IRGRP | S_IROTH);
        if (fd == -1)
            exitn(errno, "unable to create empty file %s", buf);
        close(fd);
    }
    struct stat st = {0};
    struct utimbuf new_times = {0};
    if (stat(buf, &st) != 0)
        exitn(errno, "touch unable to stat %s", buf);
    new_times.actime = st.st_atime;
    new_times.modtime = time;
    if (utime(buf, &new_times) != 0)
        exitn(errno, "touch unable to utime %s", buf);
}

#if !defined(__LP64__)
#define major(x)        ((int32_t)(((u_int32_t)(x) >> 8) & 0xff))
#define minor(x)        ((int32_t)((x) & 0xff))
#else
#define major(x)        ((int64_t)(((u_int64_t)(x) >> 8) & 0xff))
#define minor(x)        ((int64_t)((x) & 0xff))
#endif

int main(int argc, char *argv[]) {
    FILE *f = NULL;

    string str = {0};

    while (readstring(&str) > 0) {
        if (strcmp(str.buf, "lsa") == 0) {
            if (readstring(&str) > 0) {
                DIR *dp;
                dp = opendir(str.buf);
                if (dp != NULL) {
                    struct dirent *ep;
                    while ((ep = readdir(dp))) {
                        char path[PATH_MAX + 1];
                        pathjoin(path, sizeof(path), &str, ep->d_name);
                        struct stat st = {0};
                        stat(path, &st); // failed for non existent links
                        char *link = NULL;
                        switch (ep->d_type) {
                            case DT_DIR: {
                                writestring("d");
                                break;
                            }
                            case DT_LNK: {
                                link = getlink(path);
                                char *target = followlink(link);
                                if (isdir(target)) {
                                    writestring("ld");
                                } else {
                                    writestring("lf");
                                    st.st_size = getsize(target);
                                }
                                if (target != link)
                                    free(target);
                                break;
                            }
                            default: {
                                writestring("f");
                            }
                        }
                        writestringf("%lli", (long long) st.st_size);
                        writestringf("%li", (long) st.st_mtime * 1000);
                        writestring(ep->d_name);
                        if (link != NULL) {
                            writestring(link);
                            free(link);
                        }
                    }
                    closedir(dp);
                    writestring("EOF");
                } else {
                    exitn(errno, "Couldn't open the directory %s", str.buf);
                }
            }
            continue;
        }
        if (strcmp(str.buf, "rafopen") == 0) {
            char *target;
            if (readstring(&str) > 0) {
                target = strdup(str.buf);
                if (readstring(&str) > 0) {
                    if (f != NULL)
                        fclose(f);
                    f = fopen(target, str.buf);
                    if (f == NULL)
                        exitn(errno, "unable to open %s", target);
                    if (strcmp(str.buf, "rb") == 0) {
                        if (fseek(f, 0, SEEK_END) != 0)
                            exitf(f, "unable to seek for '%s', '%s'", str.buf);
                        writestringf("%li", ftell(f));
                    } else {
                        writestring("ok");
                    }
                }
                free(target);
            }
            continue;
        }
        if (strcmp(str.buf, "rafclose") == 0) {
            if (f == NULL)
                exitn(1, "file NULL");
            fclose(f);
            f = NULL;
            writestring("ok");
            continue;
        }
        if (strcmp(str.buf, "rafread") == 0) {
            long long offset;
            size_t size;
            if (readstring(&str) > 0) {
                offset = atoll(str.buf);
                if (readstring(&str) > 0) {
                    size = (size_t) atoi(str.buf);
                    if (fseek(f, offset, SEEK_SET) != 0)
                        exitf(f, "unable to seek");
                    if (size > str.bufn) {
                        if (str.buf != NULL)
                            free(str.buf);
                        str.buf = (char *) malloc(size);
                        str.bufn = size;
                    }
                    while (size > 0) {
                        size_t len = fread(str.buf, sizeof(char), (size_t) size, f);
                        if (len <= 0)
                            exitf(f, "unable to read file");
                        size_t w = fwrite(str.buf, sizeof(char), len, stdout);
                        if (w != len)
                            exitf(f, "unable to write stdout");
                        size -= len;
                    }
                    fflush(stdout);
                }
            }
            continue;
        }
        if (strcmp(str.buf, "rafwrite") == 0) {
            long long offset;
            size_t size;
            if (readstring(&str) > 0) {
                offset = atoll(str.buf);
                if (readstring(&str) > 0) {
                    size = (size_t) atoi(str.buf);
                    if (fseek(f, offset, SEEK_SET) != 0)
                        exitf(f, "unable to seek");
                    if (size > str.bufn) {
                        if (str.buf != 0)
                            free(str.buf);
                        str.buf = (char *) malloc((size_t) size);
                        str.bufn = (size_t) size;
                    }
                    while (size > 0) {
                        size_t len = fread(str.buf, sizeof(char), size, stdin);
                        if (len <= 0)
                            exitf(stdin, "unable to read stdin");
                        size_t w = fwrite(str.buf, sizeof(char), len, f);
                        if (w != len)
                            exitf(f, "unable to write file");
                        size -= len;
                    }
                    writestring("ok");
                }
            }
            continue;
        }
        if (strcmp(str.buf, "isdir") == 0) {
            if (readstring(&str) > 0) {
                if (isdir(str.buf))
                    writestring("true");
                else
                    writestring("false");
            }
            continue;
        }
        if (strcmp(str.buf, "exists") == 0) {
            if (readstring(&str) > 0) {
                if (exists(str.buf))
                    writestring("true");
                else
                    writestring("false");
            }
            continue;
        }
        if (strcmp(str.buf, "delete") == 0) {
            if (readstring(&str) > 0) {
                if (remove(str.buf) != 0)
                    exitn(errno, "fm failed for %s", str.buf);
                else
                    writestring("ok");
            }
            continue;
        }
        if (strcmp(str.buf, "mkdir") == 0) {
            if (readstring(&str) > 0) {
                if (mkdir(str.buf, 0755) != 0)
                    exitn(errno, "mkdir failed for %s", str.buf);
                else
                    writestring("ok");
            }
            continue;
        }
        if (strcmp(str.buf, "length") == 0) {
            if (readstring(&str) > 0)
                writestringf("%lli", getsize(str.buf));
            continue;
        }
        if (strcmp(str.buf, "last") == 0) {
            if (readstring(&str) > 0)
                writestringf("%li", getlast(str.buf));
            continue;
        }
        if (strcmp(str.buf, "rename") == 0) {
            if (readstring(&str) > 0) {
                char *target = strdup(str.buf);
                if (readstring(&str) > 0) {
                    if (rename(target, str.buf) != 0)
                        exitn(errno, "unable rename %s %s", target, str.buf);
                    else
                        writestring("ok");
                }
                free(target);
            }
            continue;
        }
        if (strcmp(str.buf, "ln") == 0) {
            if (readstring(&str) > 0) {
                char *target = strdup(str.buf);
                if (readstring(&str) > 0) {
                    if (symlink(target, str.buf) != 0)
                        exitn(errno, "unable ln %s", target, str.buf);
                    else
                        writestring("ok");
                }
                free(target);
            }
            continue;
        }
        if (strcmp(str.buf, "touch") == 0) {
            char *target;
            if (readstring(&str) > 0) {
                target = strdup(str.buf);
                if (readstring(&str) > 0) {
                    touch(target, atol(str.buf));
                    writestring("ok");
                }
                free(target);
            }
            continue;
        }
        if (strcmp(str.buf, "df") == 0) { // get device info from file path
            if (readstring(&str) > 0) {
#ifdef ANDROID_PIE // API16+
                struct stat64 st = {0};
                stat64(str.buf, &st);
                struct statfs64 fs = {0};
                statfs64(str.buf, &fs);
#else
                struct stat st = {0};
                stat(str.buf, &st);
                struct statfs fs = {0};
                statfs(str.buf, &fs);
#endif
                struct passwd *p = getpwuid(st.st_uid);
                struct group *g = getgrgid(st.st_gid);
                writestringf("%i:%i %i %i %i %i %i %i %i %i %i %i %s %s",
                    (int) major(st.st_dev), (int) minor(st.st_dev), (int) st.st_ino, (int) st.st_mode, (int) st.st_uid, (int) st.st_gid,
                    (int) fs.f_type, (int) fs.f_bsize, (int) fs.f_blocks, (int) fs.f_bfree, (int) fs.f_files, (int) fs.f_ffree,
                    p->pw_name, g->gr_name);
            }
            continue;
        }
        if (strcmp(str.buf, "ping") == 0) {
            writestring("pong");
            continue;
        }
        if (strcmp(str.buf, "exit") == 0)
            break;
        exitn(1, "unknown command %s", str.buf);
    }

    if (str.buf != 0)
        free(str.buf);

    return 0;
}
