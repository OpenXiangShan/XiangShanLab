std::atomic<bool> global_lock(false);

void smp_acquire_lock() {
    while (global_lock.exchange(true, std::memory_order_acquire)) {
        // Busy-wait
    }
}

void smp_release_lock() {
    global_lock.store(false, std::memory_order_release);
}

int main(long hartid) {
    smp_acquire_lock();
    print_s("Hello from hart ");
    print_digit(hartid);
    print_s("\n");
    smp_release_lock();
    while(1);
    return 0;
}
