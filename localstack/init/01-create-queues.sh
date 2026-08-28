#!/bin/bash
# Runs automatically once LocalStack is ready.
# Creates the order notification queue plus its dead letter queue, and wires
# the redrive policy so a message that fails 3 receives lands in the DLQ.
set -e
REGION=ap-south-1

awslocal sqs create-queue --queue-name oms-order-notifications-dlq --region "$REGION"

DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/oms-order-notifications-dlq \
  --attribute-names QueueArn --region "$REGION" \
  --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue \
  --queue-name oms-order-notifications \
  --region "$REGION" \
  --attributes "{\"VisibilityTimeout\":\"30\",\"MessageRetentionPeriod\":\"345600\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

echo "SQS queues ready:"
awslocal sqs list-queues --region "$REGION"
