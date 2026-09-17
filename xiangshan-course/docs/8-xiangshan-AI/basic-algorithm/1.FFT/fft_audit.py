"""Verify key claims in fft.md against numpy ground truth."""
import numpy as np

x = np.array([1, 1, 1, 1, 0, 0, 0, 0], dtype=complex)
N = len(x)

# --- Claim 1: X[0] = sum of x (DC = mean * N) ---
X = np.fft.fft(x)
print(f"X[0] = {X[0]:.4f}, sum(x) = {np.sum(x):.4f}, mean(x)*N = {np.mean(x)*N:.4f}")
assert np.isclose(X[0], np.sum(x)), "X[0] should equal sum(x)"

# --- Claim 2: X[4] = 0 (Nyquist for rectangular pulse) ---
print(f"X[4] = {X[4]:.4f}")
assert np.isclose(X[4], 0), "X[4] should be 0 for [1,1,1,1,0,0,0,0]"

# --- Claim 3: Conjugate symmetry X[k] = conj(X[N-k]) for real input ---
for k in range(1, N//2):
    assert np.isclose(X[k], np.conj(X[N-k])), f"X[{k}] != conj(X[{N-k}])"
print("Conjugate symmetry X[k] = conj(X[N-k]): OK")

# --- Claim 4: W_N^{k+N/2} = -W_N^k ---
W = lambda k: np.exp(-2j * np.pi * k / N)
for k in range(N//2):
    assert np.isclose(W(k + N//2), -W(k)), f"W({k+N//2}) != -W({k})"
print("W_N^{k+N/2} = -W_N^k: OK")

# --- Claim 5: W_N^2 = W_{N/2} ---
assert np.isclose(W(2), np.exp(-2j * np.pi / (N//2))), "W_N^2 != W_{N/2}"
print("W_N^2 = W_{N/2}: OK")

# --- Claim 6: Even/odd split for the example ---
x_even = x[0::2]
x_odd  = x[1::2]
print(f"Even indices: {x_even.real} (expected [1,1,0,0])")
print(f"Odd indices:  {x_odd.real} (expected [1,1,0,0])")
assert np.allclose(x_even, [1,1,0,0]), "Even split mismatch"
assert np.allclose(x_odd, [1,1,0,0]), "Odd split mismatch"

# --- Claim 7: Butterfly formula X[k] = E[k] + W_N^k * O[k] ---
E = np.fft.fft(x_even)  # N/2-point DFT of even samples
O = np.fft.fft(x_odd)   # N/2-point DFT of odd samples
half = N // 2
for k in range(half):
    # X[k] = E[k] + W_N^k * O[k]
    # Note: E and O are N/2-point DFTs, so E[k mod N/2] = E[k] for k in [0, N/2-1]
    expected_Xk = E[k] + W(k) * O[k]
    expected_Xk2 = E[k] - W(k) * O[k]
    assert np.isclose(X[k], expected_Xk), f"Butterfly X[{k}] mismatch"
    assert np.isclose(X[k + half], expected_Xk2), f"Butterfly X[{k+half}] mismatch"
print("Butterfly formulas X[k]=E[k]+W*O[k], X[k+N/2]=E[k]-W*O[k]: OK")

# --- Claim 8: Convolution of [1,1,1,1,0,0,0,0] * [1,-1] ---
y = np.convolve([1,1,1,1,0,0,0,0], [1,-1])
print(f"Convolution: {y} (expected [1,0,0,0,-1,0,0,0,0])")
assert np.allclose(y, [1,0,0,0,-1,0,0,0,0]), "Convolution mismatch"
print(f"y[4] = {y[4]} (jump from x[3]=1 to x[4]=0 gives -1)")

# --- Claim 9: DFT multiplication = circular convolution, with padding = linear conv ---
x_pad = np.zeros(16, dtype=complex); x_pad[:8] = [1,1,1,1,0,0,0,0]
h_pad = np.zeros(16, dtype=complex); h_pad[:2] = [1,-1]
y_fft = np.real(np.fft.ifft(np.fft.fft(x_pad) * np.fft.fft(h_pad)))[:9]
assert np.allclose(y_fft, y), "FFT convolution != direct convolution"
print("FFT convolution matches direct convolution: OK")

# --- Claim 10: O(N^2) for N=1024 > 1 million ---
N_test = 1024
print(f"N={N_test}: N^2 = {N_test**2} (> 1 million: {N_test**2 > 1_000_000})")
print(f"N={N_test}: N*log2(N) = {N_test * int(np.log2(N_test))}")

print("\n=== All claims verified ===")
