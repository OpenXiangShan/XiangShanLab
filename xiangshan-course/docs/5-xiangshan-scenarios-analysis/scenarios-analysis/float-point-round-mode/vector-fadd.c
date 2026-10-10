#include <am.h>
#include <klib.h>

#define VECTOR_LEN 4

static void init_vector_state() {
  // Enable the vector unit and clear vector control state.
  asm volatile("li t0, 0x2500\n"
               "csrs mstatus, t0\n"
               "csrwi vcsr, 0\n"
               "csrwi vstart, 0"
               :
               :
               : "t0", "memory");
}

int main() {
  init_vector_state();

  float lhs[VECTOR_LEN] = {1.0f, 1.0f, 1.0f, 1.0f};
  float rhs[VECTOR_LEN] = {0.1f, 0.1f, 0.1f, 0.1f};
  float result[VECTOR_LEN] = {0.0f, 0.0f, 0.0f, 0.0f};

  asm volatile("vsetivli zero, 4, e32, m1, ta, ma\n"
               "vle32.v v1, (%0)\n"
               "vle32.v v2, (%1)\n"
               "vfadd.vv v3, v1, v2\n"
               "vse32.v v3, (%2)\n"
               "fence rw, rw"
               :
               : "r"(lhs), "r"(rhs), "r"(result)
               : "v1", "v2", "v3", "memory");

  // printf("vfadd.vv result: %f %f %f %f\n", result[0], result[1], result[2], result[3]);

  return 0;
}
