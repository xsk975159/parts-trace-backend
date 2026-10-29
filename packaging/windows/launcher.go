//go:build windows

package main

import (
	"bufio"
	"crypto/sha256"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"unsafe"
)

var version = "1.1.0"
var kernel = syscall.NewLazyDLL("kernel32.dll")
var createJob = kernel.NewProc("CreateJobObjectW")
var setJob = kernel.NewProc("SetInformationJobObject")
var assignJob = kernel.NewProc("AssignProcessToJobObject")

type basicLimits struct {
	ProcessTime, JobTime         int64
	Flags                        uint32
	MinWorkingSet, MaxWorkingSet uintptr
	ActiveProcesses              uint32
	Affinity                     uintptr
	Priority, Scheduling         uint32
}
type extendedLimits struct {
	Basic                                                      basicLimits
	IO                                                         [6]uint64
	ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory uintptr
}

func run() int {
	check := flag.Bool("check", false, "检查环境及数据库，不启动服务")
	noBrowser := flag.Bool("no-browser", false, "启动后不打开浏览器")
	noPause := flag.Bool("no-pause", false, "退出时不等待按键")
	config := flag.String("config", "", "运行配置文件路径")
	showVersion := flag.Bool("version", false, "显示版本")
	stop := flag.Bool("stop", false, "停止本目录启动的独立运行包")
	flag.Parse()
	if *showVersion {
		fmt.Println("Parts Trace Windows Launcher " + version)
		return 0
	}
	executable, err := os.Executable()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	root := filepath.Dir(executable)
	script := filepath.Join(root, "scripts", "start-windows.ps1")
	standalone := filepath.Join(root, "scripts", "start-standalone.ps1")
	if _, e := os.Stat(standalone); e == nil {
		script = standalone
	}
	if *stop {
		if script != standalone {
			fmt.Fprintln(os.Stderr, "此入口用于独立运行包。")
			return 1
		}
		if _, e := os.Stat(filepath.Join(root, "data", "standalone-secrets.json")); e != nil {
			fmt.Fprintln(os.Stderr, "本目录尚未初始化。")
			return 1
		}
		e := os.WriteFile(filepath.Join(root, "data", "stop.request"), []byte("stop\n"), 0600)
		if e != nil {
			fmt.Fprintln(os.Stderr, e)
			return 1
		}
		fmt.Println("已请求停止平台，请等待启动窗口显示数据库已停止。")
		return 0
	}
	if _, err = os.Stat(script); err != nil {
		fmt.Fprintln(os.Stderr, "缺少启动脚本，请完整解压运行包，不要单独移动EXE。")
		return 1
	}
	args := []string{"-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script}
	// 互斥锁在启动任何子进程前取得，避免并发初始化和覆盖密钥。
	lockHash := sha256.Sum256([]byte(strings.ToLower(filepath.Clean(root))))
	mutexName, _ := syscall.UTF16PtrFromString(fmt.Sprintf("Local\\PartsTrace-%x", lockHash))
	mutex, _, mutexError := kernel.NewProc("CreateMutexW").Call(0, 1, uintptr(unsafe.Pointer(mutexName)))
	if mutex == 0 {
		fmt.Fprintln(os.Stderr, "无法创建启动锁：", mutexError)
		return 1
	}
	defer syscall.CloseHandle(syscall.Handle(mutex))
	if mutexError == syscall.Errno(183) {
		fmt.Fprintln(os.Stderr, "本目录的平台已经在运行。")
		return 1
	}
	if *check {
		args = append(args, "-CheckOnly")
	}
	if *noBrowser {
		args = append(args, "-NoBrowser")
	}
	if *config != "" {
		absolute, e := filepath.Abs(*config)
		if e != nil {
			return 1
		}
		args = append(args, "-ConfigPath", absolute)
	}
	command := exec.Command("powershell.exe", args...)
	command.Dir = root
	command.Stdin, command.Stdout, command.Stderr = os.Stdin, os.Stdout, os.Stderr
	job, _, jobError := createJob.Call(0, 0)
	if job == 0 {
		fmt.Fprintln(os.Stderr, "无法创建运行进程组：", jobError)
		return 1
	}
	defer syscall.CloseHandle(syscall.Handle(job))
	limits := extendedLimits{}
	limits.Basic.Flags = 0x2000 // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
	ok, _, jobError := setJob.Call(job, 9, uintptr(unsafe.Pointer(&limits)), unsafe.Sizeof(limits))
	if ok == 0 {
		fmt.Fprintln(os.Stderr, "无法设置运行进程组：", jobError)
		return 1
	}
	interrupts := make(chan os.Signal, 1)
	signal.Notify(interrupts, os.Interrupt)
	defer signal.Stop(interrupts)
	if err = command.Start(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	handle, err := syscall.OpenProcess(0x0101, false, uint32(command.Process.Pid))
	if err != nil {
		command.Process.Kill()
		command.Wait()
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	ok, _, jobError = assignJob.Call(job, uintptr(handle))
	syscall.CloseHandle(handle)
	if ok == 0 {
		command.Process.Kill()
		command.Wait()
		fmt.Fprintln(os.Stderr, "无法管理启动进程：", jobError)
		return 1
	}
	err = command.Wait()
	exitCode := 0
	if err != nil {
		exitCode = 1
		if e, ok := err.(*exec.ExitError); ok {
			exitCode = e.ExitCode()
		}
	}
	if !*noPause {
		fmt.Print("\n程序已停止，按回车关闭窗口。")
		bufio.NewReader(os.Stdin).ReadString('\n')
	}
	return exitCode
}

func main() {
	kernel.NewProc("SetConsoleOutputCP").Call(65001)
	kernel.NewProc("SetConsoleCP").Call(65001)
	os.Exit(run())
}
