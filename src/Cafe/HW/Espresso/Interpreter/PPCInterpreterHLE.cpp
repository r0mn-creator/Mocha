#include "../PPCState.h"
#include "PPCInterpreterInternal.h"
#include "PPCInterpreterHelper.h"

#include "config/ActiveSettings.h"
#include <mutex>
#include <cstdio>
#if defined(__ANDROID__)
#include <sys/system_properties.h>
#endif

// ---------------------------------------------------------------------------
// JIT differential verifier
//
// Records a compact fingerprint of guest CPU state at every HLE call. Run the game once with the
// recompiler and once with the interpreter, then diff the two traces: the first record that differs
// brackets the guest code between the previous HLE call and that one, which is where the JIT and the
// interpreter stopped agreeing.
//
// HLE dispatch is used as the synchronisation point because it is the only place that is reached
// identically by both execution modes. Nothing is re-executed, so there are no duplicated side
// effects (memory writes, file reads, GX2 submissions).
//
// Enable with: adb shell setprop debug.mocha.verify <slot>   (0 = off, 1 = run A, 2 = run B, ...)
// Output:      <userdata>/jittrace_<slot>.bin
// ---------------------------------------------------------------------------
namespace
{
	// Every HLE call is folded into a rolling hash; only a checkpoint is written to disk every
	// VERIFY_CHECKPOINT_INTERVAL calls. This keeps full coverage (a divergence at ANY call changes
	// the rolling hash and therefore shows up at the next checkpoint) while keeping the trace small.
	// Recording one record per call produced 1.28GB in two minutes, which was unusable.
	constexpr uint64 VERIFY_CHECKPOINT_INTERVAL = 1; // file I/O is rare enough to record every call
	constexpr uint64 VERIFY_MAX_CHECKPOINTS = 4'000'000ull; // ~64MB

	struct VerifyRecord
	{
		uint64 seq;          // index of the HLE call at this checkpoint
		uint64 rollingHash;  // accumulated over every call so far
		uint32 hleFuncId;    // of the call at the checkpoint
		uint32 lr;           // of the call at the checkpoint
	};
	static_assert(sizeof(VerifyRecord) == 24);

	std::mutex s_verifyMutex;
	FILE* s_verifyFile = nullptr;
	uint64 s_verifySeq = 0;
	uint64 s_verifyCheckpoints = 0;
	uint64 s_verifyRolling = 0xCBF29CE484222325ull;
	bool s_verifyInitDone = false;
	bool s_verifyActive = false;

	// Only trace data-driven HLE calls. Frame-driven calls (input polling, rendering, timers) fire a
	// different number of times between two runs simply because the runs take different amounts of
	// wall-clock time, which would produce a false divergence immediately. File I/O is driven by what
	// the game loads, not by how long it ran, so it stays aligned across runs.
	bool s_verifyTraceable[0x4000]{};

	inline void FoldU32(uint64& h, uint32 v)
	{
		for (int b = 0; b < 4; b++)
		{
			h ^= (uint8)(v >> (b * 8));
			h *= 0x100000001B3ull;
		}
	}
}

void PPCInterpreter_verifyTracePoint(PPCInterpreter_t* hCPU, uint32 hleFuncId)
{
	if (!s_verifyInitDone) [[unlikely]]
	{
		std::unique_lock _l(s_verifyMutex);
		if (!s_verifyInitDone)
		{
			int slot = 0;
#if defined(__ANDROID__)
			char buf[PROP_VALUE_MAX] = {};
			if (__system_property_get("debug.mocha.verify", buf) > 0)
				slot = atoi(buf);
#endif
			if (slot > 0)
			{
				fs::path path = ActiveSettings::GetUserDataPath(fmt::format("jittrace_{}.bin", slot));
				s_verifyFile = fopen(_pathToUtf8(path).c_str(), "wb");
				if (s_verifyFile)
				{
					s_verifyActive = true;
					cemuLog_log(LogType::Force, "JIT verifier: recording trace to {}", _pathToUtf8(path));
				}
				else
				{
					cemuLog_log(LogType::Force, "JIT verifier: failed to open trace file {}", _pathToUtf8(path));
				}
			}
			s_verifyInitDone = true;
		}
	}
	if (!s_verifyActive)
		return;

	if (hleFuncId >= 0x4000 || !s_verifyTraceable[hleFuncId])
		return; // frame-driven call, not comparable across runs

	std::unique_lock _l(s_verifyMutex);
	if (!s_verifyFile || s_verifyCheckpoints >= VERIFY_MAX_CHECKPOINTS)
		return;

	// fold this call's guest state into the rolling hash (integer register file + LR/CTR/CR + which
	// HLE function). remainingCycles is deliberately excluded so timing differences between runs do
	// not look like divergences.
	uint64 h = s_verifyRolling;
	for (int i = 0; i < 32; i++)
		FoldU32(h, hCPU->gpr[i]);
	FoldU32(h, hleFuncId);
	FoldU32(h, hCPU->spr.LR);
	FoldU32(h, hCPU->spr.CTR);
	uint32 cr = 0;
	for (int i = 0; i < 32; i++)
		cr |= (uint32)(hCPU->cr[i] & 1) << i;
	FoldU32(h, cr);
	s_verifyRolling = h;

	uint64 seq = s_verifySeq++;
	if ((seq % VERIFY_CHECKPOINT_INTERVAL) != 0)
		return;

	// For the first handful of traced calls also dump the full register file, so a divergence can be
	// attributed to a specific register rather than just a hash mismatch.
	if (seq < 64)
	{
		static FILE* s_detailFile = nullptr;
		if (!s_detailFile)
		{
			int slot = 0;
#if defined(__ANDROID__)
			char dbuf[PROP_VALUE_MAX] = {};
			if (__system_property_get("debug.mocha.verify", dbuf) > 0)
				slot = atoi(dbuf);
#endif
			fs::path dpath = ActiveSettings::GetUserDataPath(fmt::format("jitdetail_{}.txt", slot));
			s_detailFile = fopen(_pathToUtf8(dpath).c_str(), "w");
		}
		if (s_detailFile)
		{
			fprintf(s_detailFile, "call=%llu hleFuncId=0x%04x LR=0x%08x CTR=0x%08x\n",
				(unsigned long long)seq, hleFuncId, hCPU->spr.LR, hCPU->spr.CTR);
			for (int i = 0; i < 32; i++)
				fprintf(s_detailFile, "  r%-2d = 0x%08x\n", i, hCPU->gpr[i]);
			fflush(s_detailFile);
		}
	}

	VerifyRecord rec;
	rec.seq = seq;
	rec.rollingHash = s_verifyRolling;
	rec.hleFuncId = hleFuncId;
	rec.lr = hCPU->spr.LR;
	fwrite(&rec, sizeof(rec), 1, s_verifyFile);
	s_verifyCheckpoints++;
	fflush(s_verifyFile);
}

std::unordered_set<std::string> s_unsupportedHLECalls;

void PPCInterpreter_handleUnsupportedHLECall(PPCInterpreter_t* hCPU)
{
	const char* libFuncName = (char*)memory_getPointerFromVirtualOffset(hCPU->instructionPointer + 8);
	std::string tempString = fmt::format("Unsupported lib call: {}", libFuncName);
	if (s_unsupportedHLECalls.find(tempString) == s_unsupportedHLECalls.end())
	{
		cemuLog_log(LogType::UnsupportedAPI, "{}", tempString);
		s_unsupportedHLECalls.emplace(tempString);
	}
	hCPU->gpr[3] = 0;
	PPCInterpreter_nextInstruction(hCPU);
}

static constexpr size_t HLE_TABLE_CAPACITY = 0x4000;
HLECALL s_ppcHleTable[HLE_TABLE_CAPACITY]{};
sint32 s_ppcHleTableWriteIndex = 0;
std::mutex s_ppcHleTableMutex;

static std::string s_hleNames[HLE_TABLE_CAPACITY];
HLEIDX PPCInterpreter_registerHLECall(HLECALL hleCall, std::string hleName)
{
	std::unique_lock _l(s_ppcHleTableMutex);
	// see s_verifyTraceable: restrict the JIT verifier to deterministic, data-driven calls
	const bool isTraceable =
		hleName.find("FSReadFile") != std::string::npos ||
		hleName.find("FSOpenFile") != std::string::npos ||
		hleName.find("FSCloseFile") != std::string::npos ||
		hleName.find("FSGetStatFile") != std::string::npos ||
		hleName.find("FSSetPosFile") != std::string::npos;
	if (s_ppcHleTableWriteIndex >= HLE_TABLE_CAPACITY)
	{
		cemuLog_log(LogType::Force, "HLE table is full");
		cemu_assert(false);
	}
	for (sint32 i = 0; i < s_ppcHleTableWriteIndex; i++)
	{
		if (s_ppcHleTable[i] == hleCall)
		{
			return i;
		}
	}
	cemu_assert(s_ppcHleTableWriteIndex < HLE_TABLE_CAPACITY);
	s_ppcHleTable[s_ppcHleTableWriteIndex] = hleCall;
	if (isTraceable && s_ppcHleTableWriteIndex < 0x4000)
		s_verifyTraceable[s_ppcHleTableWriteIndex] = true;
	HLEIDX funcIndex = s_ppcHleTableWriteIndex;
	if (funcIndex < HLE_TABLE_CAPACITY)
		s_hleNames[funcIndex] = hleName;   // for the crash-time call-history dump
	s_ppcHleTableWriteIndex++;
	return funcIndex;
}

// --- crash-time HLE call history -------------------------------------------------------
// The NFS boot crash is intermittent and, on a SUCCESSFUL launch, the faulting routine is never
// called at all - so the divergence happens earlier. This ring records the last calls made on
// each thread; PPCInterpreter_dumpHleHistory() prints them when the guard trips, showing what the
// guest was doing just before it went wrong.
static constexpr uint32 HLE_HIST = 96;
struct HleHistEntry { uint32 id; uint32 lr; };
static thread_local HleHistEntry t_hleHist[HLE_HIST];
static thread_local uint32 t_hleHistPos = 0;

void PPCInterpreter_recordHleCall(uint32 hleFuncId, uint32 lr)
{
	t_hleHist[t_hleHistPos % HLE_HIST] = { hleFuncId, lr };
	t_hleHistPos++;
}

void PPCInterpreter_dumpHleHistory(const char* reason)
{
	uint32 n = t_hleHistPos < HLE_HIST ? t_hleHistPos : HLE_HIST;
	cemuLog_log(LogType::Force, "HLEHIST ({}) last {} calls on this thread, oldest first:", reason, n);
	for (uint32 i = 0; i < n; i++)
	{
		uint32 idx = (t_hleHistPos - n + i) % HLE_HIST;
		uint32 id = t_hleHist[idx].id;
		const char* nm = (id < HLE_TABLE_CAPACITY && !s_hleNames[id].empty()) ? s_hleNames[id].c_str() : "?";
		cemuLog_log(LogType::Force, "  HLEHIST[{:3}] {} (id 0x{:04x}) LR=0x{:08x}", i, nm, id, t_hleHist[idx].lr);
	}
}

HLECALL PPCInterpreter_getHLECall(HLEIDX funcIndex)
{
	if (funcIndex < 0 || funcIndex >= HLE_TABLE_CAPACITY)
		return nullptr;
	return s_ppcHleTable[funcIndex];
}

std::mutex s_hleLogMutex;

void PPCInterpreter_virtualHLE(PPCInterpreter_t* hCPU, unsigned int opcode)
{
	uint32 hleFuncId = opcode & 0xFFFF;
	if (hleFuncId == 0xFFD0) [[unlikely]]
	{
		s_hleLogMutex.lock();
		PPCInterpreter_handleUnsupportedHLECall(hCPU);
		s_hleLogMutex.unlock();
	}
	else
	{
		// os lib function
		PPCInterpreter_verifyTracePoint(hCPU, hleFuncId);
		PPCInterpreter_recordHleCall(hleFuncId, hCPU->spr.LR);
		auto hleCall = PPCInterpreter_getHLECall(hleFuncId);
		cemu_assert(hleCall);
		hleCall(hCPU);
	}
}