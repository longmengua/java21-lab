from locust import HttpUser, task, between

class DemoUser(HttpUser):
    wait_time = between(1, 5)

    @task
    def test_get_message(self):
        self.client.get("/api/message")

    @task
    def test_async_method(self):
        self.client.get("/api/async-method")

    @task
    def test_another_async_method(self):
        self.client.get("/api/another-async-method")
